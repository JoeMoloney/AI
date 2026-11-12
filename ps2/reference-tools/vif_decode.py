#!/usr/bin/env python3
"""Decode a raw PS2 VIF1 command stream independently of the runtime.

Input is the SIMS_VIF1_DUMP_DIR capture (vif1_raw.bin: repeated [u32 length][bytes]).
Transfers are concatenated, because the VIF processes one continuous stream.
Asset-free: only the public VIFcode encodings.

    python3 tools/vif_decode.py vif1_raw.bin --watch 0x30 --until-mscal 1
"""

import argparse
import struct

UNPACK_NAMES = {(0, 0): "S-32", (0, 1): "S-16", (0, 2): "S-8", (1, 0): "V2-32", (1, 1): "V2-16", (1, 2): "V2-8",
                (2, 0): "V3-32", (2, 1): "V3-16", (2, 2): "V3-8", (3, 0): "V4-32", (3, 1): "V4-16",
                (3, 2): "V4-8", (3, 3): "V4-5"}
SIMPLE = {0x00: "NOP", 0x01: "STCYCL", 0x02: "OFFSET", 0x03: "BASE", 0x04: "ITOP", 0x05: "STMOD",
          0x06: "MSKPATH3", 0x07: "MARK", 0x10: "FLUSHE", 0x11: "FLUSH", 0x13: "FLUSHA", 0x14: "MSCAL",
          0x15: "MSCALF", 0x17: "MSCNT"}


def load_stream(path):
    raw = open(path, "rb").read()
    out, pos, chunks = bytearray(), 0, 0
    while pos + 4 <= len(raw):
        (size,) = struct.unpack_from("<I", raw, pos)
        out += raw[pos + 4: pos + 4 + size]
        pos += 4 + size
        chunks += 1
    return bytes(out), chunks


def decode(data, watch=None, until_mscal=None):
    pos = 0
    cl, wl = 1, 1
    base = ofst = tops = 0
    dbf = 0
    mscals = 0
    while pos + 4 <= len(data):
        (cmd,) = struct.unpack_from("<I", data, pos)
        at = pos
        pos += 4
        op = (cmd >> 24) & 0x7F
        num = (cmd >> 16) & 0xFF
        imm = cmd & 0xFFFF
        text = None
        if op in SIMPLE:
            name = SIMPLE[op]
            if op == 0x00:
                continue
            if op == 0x01:
                cl, wl = imm & 0xFF, (imm >> 8) & 0xFF
                text = f"STCYCL cl={cl} wl={wl}"
            elif op == 0x02:
                ofst = imm & 0x3FF
                tops, dbf = base, 0
                text = f"OFFSET 0x{ofst:x}"
            elif op == 0x03:
                base = imm & 0x3FF
                text = f"BASE 0x{base:x}"
            elif op in (0x14, 0x15, 0x17):
                text = f"{name} pc=0x{imm * 8:x} top=0x{tops:x}" if op != 0x17 else f"MSCNT top=0x{tops:x}"
                tops = base if dbf else (base + ofst) & 0x3FF
                dbf ^= 1
                mscals += 1
            else:
                text = f"{name} imm=0x{imm:x}"
        elif op == 0x20:
            text = f"STMASK 0x{struct.unpack_from('<I', data, pos)[0]:08x}"
            pos += 4
        elif op in (0x30, 0x31):
            words = struct.unpack_from("<4I", data, pos)
            text = f"{'STROW' if op == 0x30 else 'STCOL'} " + " ".join(f"{w:08x}" for w in words)
            pos += 16
        elif op == 0x4A:
            count = num or 256
            text = f"MPG addr=0x{imm * 8:x} instrs={count}"
            pos += count * 8
        elif op in (0x50, 0x51):
            qwc = imm or 65536
            text = f"{'DIRECT' if op == 0x50 else 'DIRECTHL'} qwc={qwc}"
            pos += qwc * 16
        elif (op & 0x60) == 0x60:
            vn, vl, mask = (op >> 2) & 3, op & 3, (op >> 4) & 1
            count = num or 256
            elements = count if wl <= cl else cl * (count // wl) + min(count % wl, cl)
            bits = (5 * 3 + 1) if (vn, vl) == (3, 3) else (32 >> vl) * (vn + 1)
            nbytes = (elements * bits + 7) // 8
            nbytes = (nbytes + 3) & ~3
            addr = imm & 0x3FF
            flg = (imm >> 15) & 1
            dest = (addr + (tops if flg else 0)) & 0x3FF
            # destination qwords written (skipping mode when cl > wl)
            span = count if cl <= wl else (count // wl) * cl + (count % wl)
            text = (f"UNPACK {UNPACK_NAMES.get((vn, vl), '?')} num={count} addr=0x{addr:x} flg={flg} "
                    f"usn={(imm >> 14) & 1} mask={mask} -> qw 0x{dest:x}..0x{(dest + span - 1) & 0x3FF:x} "
                    f"({nbytes} data bytes)")
            if watch is not None and dest <= watch < dest + span:
                text += f"   <== writes watched qw 0x{watch:x}"
            pos += nbytes
        else:
            text = f"UNKNOWN op=0x{op:02x} cmd={cmd:08x}"
        yield at, text
        if until_mscal is not None and mscals >= until_mscal:
            return


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dump")
    parser.add_argument("--watch", type=lambda v: int(v, 0))
    parser.add_argument("--until-mscal", type=int)
    args = parser.parse_args()
    data, chunks = load_stream(args.dump)
    print(f"# {chunks} transfers, {len(data)} bytes")
    for at, text in decode(data, args.watch, args.until_mscal):
        print(f"{at:08x}: {text}")


if __name__ == "__main__":
    main()
