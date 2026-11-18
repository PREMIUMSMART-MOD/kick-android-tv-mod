"""Regresja formatu delta na syntetycznych danych, bez oryginalnego APK."""
from __future__ import annotations
import importlib.util
from pathlib import Path
import random
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("prepare_profile", ROOT / "maintainer/prepare_profile.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DeltaTests(unittest.TestCase):
    def test_insert_delete_reorder(self) -> None:
        source = random.Random(17).randbytes(128 * 1024)
        target = source[8192:40000] + b"new data" * 23 + source[:8192] + source[45000:]
        patch = module.delta(source, target)
        self.assertEqual(module.apply_delta(source, patch), target)
        self.assertLess(len(patch), len(target) // 10)

    def test_empty_and_short(self) -> None:
        for source, target in [(b"", b""), (b"", b"added"), (b"removed", b""), (b"ab", b"ac")]:
            with self.subTest(source=source, target=target):
                self.assertEqual(module.apply_delta(source, module.delta(source, target)), target)

    def test_repeated_blocks(self) -> None:
        source = bytes(range(256)) * 256
        target = b"prefix" + source[307:20000] + b"middle" + source[:513] + b"end"
        self.assertEqual(module.apply_delta(source, module.delta(source, target)), target)


if __name__ == "__main__":
    unittest.main()
