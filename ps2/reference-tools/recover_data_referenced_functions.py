#!/usr/bin/env python3
"""Recover MIPS functions referenced from ELF data (vtables, callback tables).

Unlike recover_return_stubs.py, this handles functions with internal branches.
A candidate is a word in allocated, non-executable data that points into an
executable section, is not already a known function start, and immediately
follows a `jr $ra` + delay slot (the previous function's epilogue). Its end is
found with a linear sweep that tracks the furthest forward branch target: the
function ends after the first `jr $ra` (or unconditional `j`/`b` that leaves
the function) whose delay slot lies beyond every branch target seen so far.

Outputs game-derived CSV only; keep it in an ignored local workspace.
"""

import argparse
import csv
import struct
from pathlib import Path

JR_RA = 0x03E00008
MAX_FUNCTION_BYTES = 0x4000


def signed16(value):
    return value - 0x10000 if value & 0x8000 else value


def branch_target(pc, instruction):
    """Return the target of a PC-relative branch, or None."""
    op = instruction >> 26
    rt = (instruction >> 16) & 31
    if op in (4, 5, 6, 7, 20, 21, 22, 23) or (op == 1 and rt in (0, 1, 2, 3, 16, 17, 18, 19)):
        return pc + 4 + (signed16(instruction & 0xFFFF) << 2)
    if op in (16, 17, 18) and (instruction >> 21) & 31 == 8:  # COPz BC
        return pc + 4 + (signed16(instruction & 0xFFFF) << 2)
    return None


def is_unconditional_branch(instruction):
    # beq $zero, $zero (b) and bgez $zero (b alias)
    op = instruction >> 26
    rs = (instruction >> 21) & 31
    rt = (instruction >> 16) & 31
    return (op == 4 and rs == 0 and rt == 0) or (op == 1 and rs == 0 and rt == 1)


def find_end(read_word, start, limit, known_starts):
    furthest = start
    pc = start
    while pc < limit and pc - start < MAX_FUNCTION_BYTES:
        if pc != start and pc in known_starts:
            return pc  # ran into the next known function
        instruction = read_word(pc)
        if instruction is None:
            return None
        target = branch_target(pc, instruction)
        if target is not None and target > furthest:
            furthest = target
        op = instruction >> 26
        leaves = instruction == JR_RA
        if op == 2:  # j: tail call if the target is outside what we've seen
            target_j = (pc & 0xF0000000) | ((instruction & 0x03FFFFFF) << 2)
            leaves = not (start <= target_j < pc)
        if is_unconditional_branch(instruction) and target is not None and target < start:
            leaves = True
        if leaves and pc + 4 >= furthest:
            return pc + 8
        pc += 4
    return None


def recover(elf_path, map_paths):
    data = elf_path.read_bytes()
    if data[:6] != b"\x7fELF\x01\x01":
        raise ValueError("expected a little-endian ELF32 file")
    header = struct.unpack_from("<16sHHIIIIIHHHHHH", data)
    if header[2] != 8:
        raise ValueError("expected a MIPS executable")
    sections = [struct.unpack_from("<10I", data, header[6] + i * header[11]) for i in range(header[12])]
    executable = [s for s in sections if s[1] == 1 and s[2] & 4]

    def section_for(address):
        for s in executable:
            if s[3] <= address < s[3] + s[5]:
                return s
        return None

    def read_word(address):
        s = section_for(address)
        if s is None or address + 4 > s[3] + s[5]:
            return None
        return struct.unpack_from("<I", data, s[4] + address - s[3])[0]

    known = set()
    for path in map_paths:
        with path.open(newline="") as stream:
            known.update(int(row["start"], 0) for row in csv.DictReader(stream))

    def follows_epilogue(address):
        # previous function's `jr $ra` + delay slot, optionally followed by a nop pad
        return read_word(address - 8) == JR_RA or (
            read_word(address - 12) == JR_RA and read_word(address - 4) == 0)

    candidates = set()
    for s in sections:
        if s[1] != 1 or not s[2] & 2 or s[2] & 4:
            continue
        words = [struct.unpack_from("<I", data, s[4] + o)[0] for o in range(0, s[5] - 3, 4)]
        for index, value in enumerate(words):
            if value & 3 or value in known or section_for(value) is None:
                continue
            # vtable/callback table: a neighbouring slot points at a known function
            in_table = any(0 <= index + d < len(words) and words[index + d] in known
                           for d in (-2, -1, 1, 2))
            if follows_epilogue(value) or in_table:
                candidates.add(value)

    # Code-built pointers: `lui rX, hi` then `addiu/ori rY, rX, lo` (e.g. a
    # callback stored into an object at runtime). Weaker evidence than a table
    # slot, so these must also follow a previous function's epilogue.
    for s in executable:
        last_lui = {}
        for address in range(s[3], s[3] + s[5] - 3, 4):
            instruction = read_word(address)
            op = instruction >> 26
            rs = (instruction >> 21) & 31
            rt = (instruction >> 16) & 31
            if op == 15:  # lui
                last_lui[rt] = (instruction & 0xFFFF) << 16
                continue
            if op in (9, 13) and rs in last_lui:  # addiu / ori
                low = instruction & 0xFFFF
                value = (last_lui[rs] + (signed16(low) if op == 9 else low)) & 0xFFFFFFFF
                if (not value & 3 and value not in known and section_for(value) is not None
                        and follows_epilogue(value)):
                    candidates.add(value)
            if instruction == JR_RA or op == 2:
                last_lui.clear()

    rows = []
    for start in sorted(candidates):
        s = section_for(start)
        end = find_end(read_word, start, s[3] + s[5], known | candidates)
        if end is not None:
            rows.append((f"dataref_{start:08x}", start, end))
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("elf", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("existing_maps", type=Path, nargs="+")
    args = parser.parse_args()
    rows = recover(args.elf, args.existing_maps)
    with args.output.open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["name", "start", "end", "size"])
        for name, start, end in rows:
            writer.writerow([name, f"0x{start:x}", f"0x{end:x}", end - start])
    print(f"Recovered {len(rows)} data-referenced functions")


if __name__ == "__main__":
    main()
