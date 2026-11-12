#!/usr/bin/env python3
"""Disassemble PS2 VU0/VU1 microcode (64-bit upper/lower instruction pairs).

Input is a raw micro-memory dump (e.g. vu1_code.bin from SIMS_VU1_TRACE_DIR).
Asset-free: contains only the public VU instruction encodings.

    python3 tools/vu_disasm.py vu1_code.bin --start 0x910 --end 0xc00
"""

import argparse
import struct

BC = "xyzw"
FLAGS = [(31, "I"), (30, "E"), (29, "M"), (28, "D"), (27, "T")]


def dest(instr):
    d = (instr >> 21) & 0xF
    return "".join(c for bit, c in zip((8, 4, 2, 1), "xyzw") if d & bit)


def fields(instr):
    return (instr >> 16) & 31, (instr >> 11) & 31, (instr >> 6) & 31  # ft, fs, fd


def s11(v):
    v &= 0x7FF
    return v - 0x800 if v & 0x400 else v


def s5(v):
    v &= 0x1F
    return v - 0x20 if v & 0x10 else v


UPPER = {}
for base, name in ((0x00, "ADD"), (0x04, "SUB"), (0x08, "MADD"), (0x0C, "MSUB"),
                   (0x10, "MAX"), (0x14, "MINI"), (0x18, "MUL")):
    for i in range(4):
        UPPER[base + i] = (name + BC[i], "bc")
UPPER.update({0x1C: ("MULq", "q"), 0x1D: ("MAXi", "i"), 0x1E: ("MULi", "i"), 0x1F: ("MINIi", "i"),
              0x20: ("ADDq", "q"), 0x21: ("MADDq", "q"), 0x22: ("ADDi", "i"), 0x23: ("MADDi", "i"),
              0x24: ("SUBq", "q"), 0x25: ("MSUBq", "q"), 0x26: ("SUBi", "i"), 0x27: ("MSUBi", "i"),
              0x28: ("ADD", "3"), 0x29: ("MADD", "3"), 0x2A: ("MUL", "3"), 0x2B: ("MAX", "3"),
              0x2C: ("SUB", "3"), 0x2D: ("MSUB", "3"), 0x2E: ("OPMSUB", "3"), 0x2F: ("MINI", "3")})

UPPER_SPECIAL = {}
for base, name in ((0x00, "ADDA"), (0x04, "SUBA"), (0x08, "MADDA"), (0x0C, "MSUBA"), (0x18, "MULA")):
    for i in range(4):
        UPPER_SPECIAL[base + i] = (name + BC[i], "abc")
for i, n in enumerate((0, 4, 12, 15)):
    UPPER_SPECIAL[0x10 + i] = (f"ITOF{n}", "2")
    UPPER_SPECIAL[0x14 + i] = (f"FTOI{n}", "2")
UPPER_SPECIAL.update({0x1C: ("MULAq", "aq"), 0x1D: ("ABS", "2"), 0x1E: ("MULAi", "ai"), 0x1F: ("CLIP", "clip"),
                      0x20: ("ADDAq", "aq"), 0x21: ("MADDAq", "aq"), 0x22: ("ADDAi", "ai"), 0x23: ("MADDAi", "ai"),
                      0x24: ("SUBAq", "aq"), 0x25: ("MSUBAq", "aq"), 0x26: ("SUBAi", "ai"), 0x27: ("MSUBAi", "ai"),
                      0x28: ("ADDA", "a3"), 0x29: ("MADDA", "a3"), 0x2A: ("MULA", "a3"), 0x2C: ("SUBA", "a3"),
                      0x2D: ("MSUBA", "a3"), 0x2E: ("OPMULA", "a3"), 0x2F: ("NOP", "none")})


def upper(instr):
    ft, fs, fd = fields(instr)
    op = instr & 0x3F
    d = dest(instr)
    if op >= 0x3C:
        name, kind = UPPER_SPECIAL.get(((instr >> 6) & 0x1F) << 2 | (instr & 3), (f"upper?{instr:08x}", "none"))
        if kind == "none":
            return name
        if kind == "abc":
            return f"{name}.{d} ACC, vf{fs}, vf{ft}{BC[instr & 3]}"
        if kind == "2":
            return f"{name}.{d} vf{ft}, vf{fs}"
        if kind == "aq":
            return f"{name}.{d} ACC, vf{fs}, Q"
        if kind == "ai":
            return f"{name}.{d} ACC, vf{fs}, I"
        if kind == "a3":
            return f"{name}.{d} ACC, vf{fs}, vf{ft}"
        if kind == "clip":
            return f"CLIPw.xyz vf{fs}, vf{ft}w"
    name, kind = UPPER.get(op, (f"upper?{instr:08x}", "none"))
    if kind == "bc":
        return f"{name}.{d} vf{fd}, vf{fs}, vf{ft}{BC[op & 3]}"
    if kind in ("q", "i"):
        return f"{name}.{d} vf{fd}, vf{fs}, {kind.upper()}"
    if kind == "3":
        return f"{name}.{d} vf{fd}, vf{fs}, vf{ft}"
    return name


LOWER_SPECIAL2 = {0x30: "MOVE", 0x31: "MR32", 0x34: "LQI", 0x35: "SQI", 0x36: "LQD", 0x37: "SQD",
                  0x38: "DIV", 0x39: "SQRT", 0x3A: "RSQRT", 0x3B: "WAITQ", 0x3C: "MTIR", 0x3D: "MFIR",
                  0x3E: "ILWR", 0x3F: "ISWR", 0x40: "RNEXT", 0x41: "RGET", 0x42: "RINIT", 0x43: "RXOR",
                  0x64: "MFP", 0x68: "XTOP", 0x69: "XITOP", 0x6C: "XGKICK", 0x70: "ESADD", 0x71: "ERSADD",
                  0x72: "ELENG", 0x73: "ERLENG", 0x74: "EATANxy", 0x75: "EATANxz", 0x76: "ESUM",
                  0x78: "ESQRT", 0x79: "ERSQRT", 0x7A: "ERCPR", 0x7B: "WAITP", 0x7C: "ESIN", 0x7D: "EATAN",
                  0x7E: "EEXP"}


def lower(instr, pc):
    if instr in (0, 0x8000033C):
        return "NOP"
    ft, fs, fd = fields(instr)
    d = dest(instr)
    op = instr >> 25
    imm = s11(instr)
    target = lambda: f"0x{(pc + 8 + imm * 8) & 0x3FFF:04x}"
    if op == 0x40:
        funct = instr & 0x3F
        if funct == 0x30:
            return f"IADD vi{fd}, vi{fs}, vi{ft}"
        if funct == 0x31:
            return f"ISUB vi{fd}, vi{fs}, vi{ft}"
        if funct == 0x32:
            return f"IADDI vi{ft}, vi{fs}, {s5(instr >> 6)}"
        if funct == 0x34:
            return f"IAND vi{fd}, vi{fs}, vi{ft}"
        if funct == 0x35:
            return f"IOR vi{fd}, vi{fs}, vi{ft}"
        if funct >= 0x3C:
            op2 = ((instr >> 6) & 0x1F) << 2 | (instr & 3)
            name = LOWER_SPECIAL2.get(op2, f"lower?{instr:08x}")
            if name in ("MOVE", "MR32"):
                return f"{name}.{d} vf{ft}, vf{fs}"
            if name in ("LQI",):
                return f"LQI.{d} vf{ft}, (vi{fs}++)"
            if name in ("SQI",):
                return f"SQI.{d} vf{fs}, (vi{ft}++)"
            if name in ("LQD",):
                return f"LQD.{d} vf{ft}, (--vi{fs})"
            if name in ("SQD",):
                return f"SQD.{d} vf{fs}, (--vi{ft})"
            if name in ("DIV", "RSQRT"):
                fsf, ftf = (instr >> 21) & 3, (instr >> 23) & 3
                return f"{name} Q, vf{fs}{BC[fsf]}, vf{ft}{BC[ftf]}"
            if name == "SQRT":
                return f"SQRT Q, vf{ft}{BC[(instr >> 23) & 3]}"
            if name == "MTIR":
                return f"MTIR vi{ft}, vf{fs}{BC[(instr >> 21) & 3]}"
            if name == "MFIR":
                return f"MFIR.{d} vf{ft}, vi{fs}"
            if name in ("ILWR", "ISWR"):
                return f"{name}.{d} vi{ft}, (vi{fs})"
            if name in ("XTOP", "XITOP"):
                return f"{name} vi{ft}"
            if name == "XGKICK":
                return f"XGKICK vi{fs}"
            if name in ("MFP", "RNEXT", "RGET"):
                return f"{name}.{d} vf{ft}"
            if name in ("RINIT", "RXOR"):
                return f"{name} R, vf{fs}{BC[(instr >> 21) & 3]}"
            if name in ("WAITQ", "WAITP"):
                return name
            return f"{name} P, vf{fs}"
        return f"lower?{instr:08x}"
    if op == 0x00:
        return f"LQ.{d} vf{ft}, {imm}(vi{fs})"
    if op == 0x01:
        return f"SQ.{d} vf{fs}, {imm}(vi{ft})"
    if op == 0x04:
        return f"ILW.{d} vi{ft}, {imm}(vi{fs})"
    if op == 0x05:
        return f"ISW.{d} vi{ft}, {imm}(vi{fs})"
    if op in (0x08, 0x09):
        imm15 = ((instr >> 10) & 0x7800) | (instr & 0x7FF)
        return f"{'IADDIU' if op == 8 else 'ISUBIU'} vi{ft}, vi{fs}, 0x{imm15:x}"
    if 0x10 <= op <= 0x1C:
        names = {0x10: "FCEQ", 0x11: "FCSET", 0x12: "FCAND", 0x13: "FCOR", 0x14: "FSEQ", 0x15: "FSSET",
                 0x16: "FSAND", 0x17: "FSOR", 0x18: "FMEQ", 0x1A: "FMAND", 0x1B: "FMOR", 0x1C: "FCGET"}
        name = names.get(op, f"lower?{instr:08x}")
        if op in (0x10, 0x11, 0x12, 0x13):
            return f"{name} vi1, 0x{instr & 0xFFFFFF:06x}"
        if op == 0x1C:
            return f"FCGET vi{ft}"
        if op in (0x18, 0x1A, 0x1B):
            return f"{name} vi{ft}, vi{fs}"
        return f"{name} vi{ft}, 0x{(instr & 0x7FF) | ((instr >> 10) & 0x800):x}"
    if op == 0x20:
        return f"B {target()}"
    if op == 0x21:
        return f"BAL vi{ft}, {target()}"
    if op == 0x24:
        return f"JR vi{fs}"
    if op == 0x25:
        return f"JALR vi{ft}, vi{fs}"
    branches = {0x28: "IBEQ", 0x29: "IBNE"}
    if op in branches:
        return f"{branches[op]} vi{ft}, vi{fs}, {target()}"
    single = {0x2C: "IBLTZ", 0x2D: "IBGTZ", 0x2E: "IBLEZ", 0x2F: "IBGEZ"}
    if op in single:
        return f"{single[op]} vi{fs}, {target()}"
    return f"lower?{instr:08x}"


def disassemble(code, start, end):
    for pc in range(start, end, 8):
        lo, up = struct.unpack_from("<II", code, pc)
        flags = "".join(name for bit, name in FLAGS if up & (1 << bit))
        lower_text = f"LOI {struct.unpack('<f', struct.pack('<I', lo))[0]:g}" if up & (1 << 31) else lower(lo, pc)
        tag = f"[{flags}]" if flags else ""
        yield f"{pc:04x}: {up:08x} {lo:08x}  {upper(up & 0x07FFFFFF):34s} | {lower_text} {tag}"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("code")
    parser.add_argument("--start", type=lambda v: int(v, 0), default=0)
    parser.add_argument("--end", type=lambda v: int(v, 0), default=None)
    args = parser.parse_args()
    code = open(args.code, "rb").read()
    for line in disassemble(code, args.start, args.end or len(code)):
        print(line)


if __name__ == "__main__":
    main()
