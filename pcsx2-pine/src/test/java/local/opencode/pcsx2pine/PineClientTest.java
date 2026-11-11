package local.opencode.pcsx2pine;

import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PineClientTest {
    @Test
    void resolvesDefaultAndAlternateLinuxSockets() {
        assertEquals(Path.of("/run/user/1000/pcsx2.sock"),
                PineClient.resolveSocketPath(Map.of("XDG_RUNTIME_DIR", "/run/user/1000")));
        assertEquals(Path.of("/run/user/1000/pcsx2.sock.28012"), PineClient.resolveSocketPath(
                Map.of("XDG_RUNTIME_DIR", "/run/user/1000", "PCSX2_PINE_SLOT", "28012")));
        assertEquals(Path.of("/tmp/pcsx2.sock"), PineClient.resolveSocketPath(Map.of()));
        assertEquals(Path.of("/tmp/custom.sock"), PineClient.resolveSocketPath(
                Map.of("PCSX2_PINE_SOCKET", "/tmp/custom.sock", "PCSX2_PINE_SLOT", "n/a")));
        assertThrows(IllegalArgumentException.class,
                () -> PineClient.resolveSocketPath(Map.of("PCSX2_PINE_SLOT", "70000")));
    }

    @Test
    void readsStatusStringAndMemoryOverFramedSocket() throws Exception {
        withPineServer(7, request -> {
            int opcode = Byte.toUnsignedInt(request[0]);
            if (opcode == 0x0f) {
                return response(new byte[] {0, 0, 0, 0, 0});
            }
            if (opcode == 0x0b) {
                return stringResponse("Test Game");
            }
            if (opcode == 0x0c) {
                return stringResponse("SLES-12345");
            }
            if (opcode == 0x0d) {
                return stringResponse("deadbeef");
            }
            if (opcode == 0x0e) {
                return stringResponse("1.00");
            }
            if (opcode == 0x08) {
                return stringResponse("PCSX2 v2.9.162");
            }
            if (opcode == 0x00) {
                if (request.length % 5 != 0) {
                    throw new AssertionError("memory request was not a sequence of 5-byte commands");
                }
                byte[] memory = new byte[request.length / 5 + 1];
                for (int i = 0; i < request.length / 5; i++) {
                    if (request[i * 5] != 0) {
                        throw new AssertionError("unexpected memory opcode");
                    }
                    memory[i + 1] = (byte) ByteBuffer.wrap(request, i * 5 + 1, 4)
                            .order(ByteOrder.LITTLE_ENDIAN).getInt();
                }
                return response(memory);
            }
            throw new AssertionError("unexpected PINE opcode " + opcode);
        }, client -> {
            assertEquals(new PineClient.EmulatorStatus(0, "running"), client.getStatus());
            assertEquals(new PineClient.GameInfo("Test Game", "SLES-12345", "deadbeef", "1.00", "PCSX2 v2.9.162"),
                    client.getGameInfo());
            assertArrayEquals(new byte[] {0, 1, 2, 3}, client.readMemory(0x1000, 4));
        });
    }

    @Test
    void rejectsInvalidMemoryRangeBeforeConnecting() {
        PineClient client = new PineClient(Path.of("/no/such/pine.sock"));
        assertThrows(IllegalArgumentException.class, () -> client.readMemory(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> client.readMemory(0, 257));
        assertThrows(IllegalArgumentException.class, () -> client.readMemory(0xffff_ffffL, 2));
    }

    private static void withPineServer(int requests, RequestHandler handler, ClientAction action) throws Exception {
        Path socketPath = Files.createTempFile("pcsx2-pine-test-", ".sock");
        Files.delete(socketPath);
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socketPath));
            Thread responder = new Thread(() -> {
                try {
                    for (int i = 0; i < requests; i++) {
                        try (SocketChannel channel = server.accept()) {
                            ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                            readFully(channel, header);
                            header.flip();
                            int length = header.getInt();
                            ByteBuffer request = ByteBuffer.allocate(length - 4);
                            readFully(channel, request);
                            byte[] reply = handler.reply(request.array());
                            ByteBuffer framedReply = ByteBuffer.allocate(reply.length + 4).order(ByteOrder.LITTLE_ENDIAN);
                            framedReply.putInt(framedReply.capacity()).put(reply).flip();
                            while (framedReply.hasRemaining()) {
                                channel.write(framedReply);
                            }
                        }
                    }
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            responder.setDaemon(true);
            responder.start();
            action.run(new PineClient(socketPath));
            responder.join(1000);
        } finally {
            Files.deleteIfExists(socketPath);
        }
    }

    private static byte[] response(byte[] payload) {
        return payload;
    }

    private static byte[] stringResponse(String value) {
        byte[] text = (value + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer payload = ByteBuffer.allocate(1 + 4 + text.length).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 0).putInt(text.length).put(text);
        return payload.array();
    }

    private static void readFully(SocketChannel channel, ByteBuffer target) throws IOException {
        while (target.hasRemaining()) {
            if (channel.read(target) < 0) {
                throw new EOFException("socket closed before request was complete");
            }
        }
    }

    @FunctionalInterface
    private interface ClientAction {
        void run(PineClient client) throws Exception;
    }

    @FunctionalInterface
    private interface RequestHandler {
        byte[] reply(byte[] request);
    }
}
