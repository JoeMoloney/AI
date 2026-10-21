"""Check the profiles' ordered permission rules without launching OpenCode."""

from fnmatch import fnmatchcase
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


def load_rules(profile):
    """Read the profiles' restricted action/resource/effect frontmatter format."""
    source = (ROOT / ".opencode/agents" / f"{profile}.md").read_text()
    frontmatter = source.split("---", 2)[1]
    permissions = frontmatter.split("permissions:\n", 1)[1]
    pattern = re.compile(
        r'  - action: ([^\n]+)\n    resource: ([^\n]+)\n    effect: (allow|deny|ask)\n?'
    )
    matches = list(pattern.finditer(permissions))
    if not matches or pattern.sub("", permissions).strip():
        raise ValueError("Profile permission format changed; review the regression checks")
    return [tuple(value.strip('\"\'') for value in match.groups()) for match in matches]


def resolve(rules, action, resource):
    # OpenCode V2 evaluates the last matching rule, not the first.
    effect = "ask"
    for action_pattern, resource_pattern, candidate in rules:
        if fnmatchcase(action, action_pattern) and fnmatchcase(resource, resource_pattern):
            effect = candidate
    return effect


class PermissionTests(unittest.TestCase):
    def test_worker_can_read_only_its_explicit_context_files(self):
        rules = load_rules("re-worker")
        for path in ("AGENTS.md", ".re-work/FableII-ledger.json"):
            self.assertEqual(resolve(rules, "read", path), "allow", path)
        for path in ("opencode.json", "memory-db/.pkey", "source.bin"):
            self.assertEqual(resolve(rules, "read", path), "deny", path)

    def test_worker_can_use_read_only_tools_and_save_memory(self):
        rules = load_rules("re-worker")
        for action in ("execute", "ghidra_get_program_info", "ghidra_list_functions",
                       "ghidra_decompile_function", "ghidra_disassemble_function",
                       "ghidra_read_memory", "memory_memory_search", "memory_memory_get",
                       "memory_memory_store"):
            self.assertEqual(resolve(rules, action, "*"), "allow", action)
        for action in ("shell", "subagent", "webfetch", "ghidra_rename_function_by_address",
                       "memory_memory_update"):
            self.assertEqual(resolve(rules, action, "*"), "deny", action)
        self.assertEqual(resolve(rules, "edit", ".re-work/FableII-ledger.json"), "deny")

    def test_coordinator_can_edit_ledger_and_delegate_only_to_worker(self):
        rules = load_rules("re-coordinator")
        self.assertEqual(resolve(rules, "edit", ".re-work/FableII-ledger.json"), "allow")
        self.assertEqual(resolve(rules, "edit", "AGENTS.md"), "deny")
        self.assertEqual(resolve(rules, "subagent", "re-worker"), "allow")
        self.assertEqual(resolve(rules, "subagent", "general"), "deny")
        self.assertEqual(resolve(rules, "memory_memory_get", "*"), "allow")
        for action in ("shell", "memory_memory_store", "ghidra_get_program_info"):
            self.assertEqual(resolve(rules, action, "*"), "deny", action)


if __name__ == "__main__":
    unittest.main()
