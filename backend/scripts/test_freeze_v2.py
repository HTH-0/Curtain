# -*- coding: utf-8 -*-
"""동결 도구 시험(명세 AC-13.11). 서버와 git이 필요 없다."""
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import freeze_v2 as fz  # noqa: E402


def make_tree(files):
    root = Path(tempfile.mkdtemp())
    for rel, data in files.items():
        p = root / fz.DETECT_DIR / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
    return root


class CodeHashTest(unittest.TestCase):
    def test_같은_내용은_같은_해시(self):
        a = make_tree({"A.java": b"class A {}\n", "B.java": b"class B {}\n"})
        b = make_tree({"B.java": b"class B {}\n", "A.java": b"class A {}\n"})
        self.assertEqual(fz.code_hash(a), fz.code_hash(b))

    def test_줄바꿈_차이는_무시한다(self):
        a = make_tree({"A.java": b"class A {\n}\n"})
        b = make_tree({"A.java": b"class A {\r\n}\r\n"})
        self.assertEqual(fz.code_hash(a), fz.code_hash(b))

    def test_내용이나_파일_이름이_다르면_다른_해시(self):
        base = fz.code_hash(make_tree({"A.java": b"class A {}\n"}))
        self.assertNotEqual(base, fz.code_hash(make_tree({"A.java": b"class A { }\n"})))
        self.assertNotEqual(base, fz.code_hash(make_tree({"B.java": b"class A {}\n"})))

    def test_파일을_더하면_다른_해시(self):
        base = fz.code_hash(make_tree({"A.java": b"class A {}\n"}))
        self.assertNotEqual(base, fz.code_hash(make_tree({"A.java": b"class A {}\n", "N.java": b"class N {}\n"})))


class CompareTest(unittest.TestCase):
    def test_다른_항목을_찾는다(self):
        frozen = {"detection_code_sha256": "a", "company_dictionary_sha256": "b", "application_yml_sha256": "c",
                  "sets": {"x": {"sha256": "1"}, "y": {"sha256": "2"}}}
        same = {**frozen, "sets": {"x": {"sha256": "1"}, "y": {"sha256": "2"}}}
        self.assertEqual(fz.compare(frozen, same), [])
        changed = {**frozen, "detection_code_sha256": "z", "sets": {"x": {"sha256": "1"}, "y": {"sha256": "9"}}}
        self.assertEqual(fz.compare(frozen, changed), ["detection_code_sha256", "sets.y"])


if __name__ == "__main__":
    unittest.main()
