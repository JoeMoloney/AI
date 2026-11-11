package local.opencode.pcsx2pine;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public final class PineClient {
    public static final int DEFAULT_SLOT = 28011;
    private static final int MAX_PACKET_LENGTH = 450_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    public enum Command {
        READ8(0x00), VERSION(0x08), TITLE(0x0b), ID(0x0c), UUID(0x0d), GAME_VERSION(0x0e), STATUS(0x0f);

        private final int opcode;

        Command(int opcode) {
            this.opcode = opcode;
        }
    }

    public record EmulatorStatus(long value, String state) {}

    public record GameInfo(String title, String id, String crc, String gameVersion, String pcsx2Version) {}

    private final Path socketPath;

    public PineClient() {
        this(resolveSocketPath(System.getenv()));
    }

    public PineClient(Path socketPath) {
        this.socketPath = socketPath;
    }

    public Path socketPath() {
        return socketPath;
    }

    public static Path resolveSocketPath(Map<String, String> env) {
        String explicit = env.get("PCSX2_PINE_SOCKET");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }

        int slot;
        try {
            slot = Integer.parseInt(env.getOrDefault("PCSX2_PINE_SLOT", Integer.toString(DEFAULT_SLOT)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("PCSX2_PINE_SLOT must be an integer between 1024 and 65535");
        }
        if (slot < 1024 || slot > 65535) {
            throw new IllegalArgumentException("PCSX2_PINE_SLOT must be an integer between 1024 and 65535");
        }

        String runtimeDir = env.get("XDG_RUNTIME_DIR");
        String base = runtimeDir == null || runtimeDir.isBlank()
                ? "/tmp/pcsx2.sock"
                : runtimeDir.replaceAll("/+$", "") + "/pcsx2.sock";
        return Path.of(slot == DEFAULT_SLOT ? base : base + "." + slot);
    }

    public EmulatorStatus getStatus() throws IOException {
        byte[] response = request(new byte[] {(byte) Command.STATUS.opcode});
        if (response.length != 4) {
            throw new IOException("Unexpected PINE status response size");
        }
        long value = Integer.toUnsignedLong(ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).getInt());
        String state = switch ((int) value) {
            case 0 -> "running";
            case 1 -> "paused";
            case 2 -> "shutdown";
            default -> "unknown";
        };
        return new EmulatorStatus(value, state);
    }

    public GameInfo getGameInfo() throws IOException {
        return new GameInfo(
                getString(Command.TITLE),
                getString(Command.ID),
                getString(Command.UUID),
                getString(Command.GAME_VERSION),
                getString(Command.VERSION));
    }

    public byte[] readMemory(long address, int length) throws IOException {
        if (address < 0 || address > 0xffff_ffffL) {
            throw new IllegalArgumentException("address must be an unsigned 32-bit integer");
        }
        if (length < 1 || length > 256) {
            throw new IllegalArgumentException("length must be an integer between 1 and 256 bytes");
        }
        if (address + length - 1 > 0xffff_ffffL) {
            throw new IllegalArgumentException("address range exceeds the 32-bit address space");
        }

        ByteBuffer request = ByteBuffer.allocate(length * 5).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset = 0; offset < length; offset++) {
            request.put((byte) Command.READ8.opcode).putInt((int) (address + offset));
        }
        byte[] response = request(request.array());
        if (response.length != length) {
            throw new IOException("Expected " + length + " PINE memory bytes, received " + response.length);
        }
        return response;
    }

    private String getString(Command command) throws IOException {
        byte[] response = request(new byte[] {(byte) command.opcode});
        if (response.length < 4) {
            throw new IOException("Unexpected PINE string response");
        }
        long length = Integer.toUnsignedLong(ByteBuffer.wrap(response, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        if (length < 1 || length > response.length - 4) {
            throw new IOException("Malformed PINE string response");
        }
        int contentLength = (int) length;
        if (response[4 + contentLength - 1] == 0) {
            contentLength--;
        }
        return new String(response, 4, contentLength, StandardCharsets.UTF_8);
    }

    private byte[] request(byte[] payload) throws IOException {
        ByteBuffer packet = ByteBuffer.allocate(payload.length + 4).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(packet.capacity()).put(payload).flip();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();

        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.configureBlocking(false);
            channel.connect(UnixDomainSocketAddress.of(socketPath));
            SelectionKey key = channel.register(selector, SelectionKey.OP_CONNECT);
            while (!channel.finishConnect()) {
                await(selector, key, SelectionKey.OP_CONNECT, deadline);
            }

            key.interestOps(SelectionKey.OP_WRITE);
            while (packet.hasRemaining()) {
                if (channel.write(packet) == 0) {
                    await(selector, key, SelectionKey.OP_WRITE, deadline);
                }
            }

            ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            key.interestOps(SelectionKey.OP_READ);
            readFully(channel, selector, key, header, deadline);
            header.flip();
            int packetLength = header.getInt();
            if (packetLength < 5 || packetLength > MAX_PACKET_LENGTH) {
                throw new IOException("Invalid PINE response length: " + Integer.toUnsignedString(packetLength));
            }

            ByteBuffer response = ByteBuffer.allocate(packetLength - 4);
            readFully(channel, selector, key, response, deadline);
            byte[] bytes = response.array();
            if (bytes[0] != 0) {
                throw new IOException("PINE returned error code 0x" + Integer.toHexString(Byte.toUnsignedInt(bytes[0])));
            }
            return java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        } catch (IOException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Timed out")) {
                throw exception;
            }
            throw new IOException("Cannot communicate with PINE socket " + socketPath + ": " + exception.getMessage(), exception);
        }
    }

    private static void readFully(SocketChannel channel, Selector selector, SelectionKey key, ByteBuffer target,
                                  long deadline) throws IOException {
        while (target.hasRemaining()) {
            int count = channel.read(target);
            if (count < 0) {
                throw new EOFException("PINE disconnected before completing its response");
            }
            if (count == 0) {
                await(selector, key, SelectionKey.OP_READ, deadline);
            }
        }
    }

    private static void await(Selector selector, SelectionKey key, int operation, long deadline) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new IOException("Timed out waiting for PINE response");
        }
        key.interestOps(operation);
        long millis = Math.max(1, Duration.ofNanos(remaining).toMillis());
        if (selector.select(millis) == 0 && System.nanoTime() >= deadline) {
            throw new IOException("Timed out waiting for PINE response");
        }
        selector.selectedKeys().clear();
    }
}
