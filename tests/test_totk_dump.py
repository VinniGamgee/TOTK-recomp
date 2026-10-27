import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("totk_dump", ROOT / "tools/totk_dump.py")
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def nso():
    b = bytearray(268)
    b[:4] = b"NSO0"
    for i in range(3):
        struct.pack_into("<III", b, 0x10 + 16 * i, 256 + 4 * i, 4096 * i, 4)
    struct.pack_into("<I", b, 0x3c, 8)
    return b


class DumpTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.profile = {"title_id": "synthetic", "source": {}, "exefs_sha256": {}}
        for name in ("main", "main.npdm", "rtld", "sdk", "subsdk0"):
            data = b"META" + bytes(124) if name == "main.npdm" else nso()
            (self.root / name).write_bytes(data)
            self.profile["exefs_sha256"][name] = hashlib.sha256(data).hexdigest()

    def test_complete_dump_and_image_size(self):
        r = mod.inspect_dump(self.root, self.profile)
        self.assertTrue(r["exefs_matches_reference"])
        self.assertEqual(r["runtime_status"], "not_ported")
        self.assertEqual(r["modules"][0]["image_size"], 8204)
        self.assertNotIn(str(self.root), json.dumps(r))

    def test_missing_third_module_rejected(self):
        (self.root / "subsdk0").unlink()
        r = mod.inspect_dump(self.root, self.profile)
        self.assertFalse(r["exefs_matches_reference"])
        self.assertEqual(r["modules"][-1]["status"], "missing")

    def test_corruption_rejected(self):
        with (self.root / "sdk").open("ab") as f:
            f.write(b"modified")
        self.assertFalse(mod.inspect_dump(self.root, self.profile)["exefs_matches_reference"])

    def test_truncated_and_out_of_bounds_headers_rejected_even_with_matching_hash(self):
        for data in (b"NSO0", nso()):
            if len(data) > 4:
                struct.pack_into("<I", data, 0x10, 99999)
            (self.root / "main").write_bytes(data)
            r = mod.inspect_module(self.root / "main", hashlib.sha256(data).hexdigest())
            self.assertEqual(r["status"], "mismatch")
            self.assertIn("error", r)

    def test_parent_folder_and_analysis_gate(self):
        extracted = self.root / "exefs"
        extracted.mkdir()
        for name in self.profile["exefs_sha256"]:
            (self.root / name).rename(extracted / name)
        (self.root / "romfs").mkdir()
        self.assertTrue(mod.inspect_dump(self.root, self.profile)["exefs_matches_reference"])
        (extracted / "sdk").unlink()
        r = mod.inspect_dump(self.root, self.profile, analyze=True)
        self.assertIn("analysis_error", r)
        self.assertEqual(r["romfs"], "folder_seen_not_validated")

    def test_profile_has_expected_five_files(self):
        profile = json.loads((ROOT / "profiles/totk-1.4.3.json").read_text())
        self.assertEqual(set(profile["exefs_sha256"]), set(self.profile["exefs_sha256"]))
        self.assertEqual(profile["display_version"], "1.4.3")
        self.assertTrue(all(len(v) == 64 for v in profile["exefs_sha256"].values()))


if __name__ == "__main__":
    unittest.main()
