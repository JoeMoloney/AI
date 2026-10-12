"""Target isolation/identity tests; no Ghidra or game binaries are needed."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("ghidra_runner", ROOT / "ghidra-mcp/run_headless.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class TargetTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.data = self.root / "data"
        self.projects = self.root / "projects"
        self.data.mkdir()
        self.binary = self.data / "sample.bin"
        self.binary.write_bytes(b"synthetic fixture; not executable")

    def prepare(self, **extra):
        env = {"GHIDRA_BINARY": str(self.binary), **extra}
        return runner.prepare_target(env, self.data, self.projects)

    def test_same_target_reuses_project_and_preserves_original(self):
        first = self.prepare()
        self.assertEqual(first, self.prepare())
        manifest = json.loads((self.projects / f"{first[1]}.target.json").read_text())
        self.assertEqual(manifest["binary"], "sample.bin")
        self.assertEqual(self.binary.read_bytes(), b"synthetic fixture; not executable")

    def test_missing_target_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Set GHIDRA_BINARY"):
            runner.prepare_target({}, self.data, self.projects)

    def test_outside_target_and_symlink_escape_are_rejected(self):
        outside = self.root / "outside.bin"
        outside.write_bytes(b"outside")
        link = self.data / "link.bin"
        link.symlink_to(outside)
        for path in (outside, link, self.data):
            with self.subTest(path=path):
                with self.assertRaisesRegex(ValueError, "regular file under /data"):
                    self.prepare(GHIDRA_BINARY=str(path))

    def test_project_name_cannot_escape_directory(self):
        for name in ("../escape", "/outside", ".", "..", "with/slash"):
            with self.subTest(name=name):
                with self.assertRaisesRegex(ValueError, "simple name"):
                    self.prepare(GHIDRA_PROJECT_NAME=name)

    def test_changed_binary_cannot_reuse_named_project(self):
        self.prepare(GHIDRA_PROJECT_NAME="test")
        self.binary.write_bytes(b"different bytes")
        with self.assertRaisesRegex(ValueError, "another binary"):
            self.prepare(GHIDRA_PROJECT_NAME="test")

    def test_existing_project_without_manifest_is_not_adopted(self):
        self.projects.mkdir()
        (self.projects / "test.gpr").write_text("existing user project")
        with self.assertRaisesRegex(ValueError, "no target manifest"):
            self.prepare(GHIDRA_PROJECT_NAME="test")


if __name__ == "__main__":
    unittest.main()
