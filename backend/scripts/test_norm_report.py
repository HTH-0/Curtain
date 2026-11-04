# -*- coding: utf-8 -*-
"""정규화 비교 보고서 도구 시험(명세 AC-13.11). 서버가 필요 없다."""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import norm_report as nr  # noqa: E402


def result(by_transform, fp=0, n=10):
    return {"by_transform": by_transform, "y": {"n": n, "false_positives": fp},
            "misses": [], "x": {}, "x_by_item": {}, "action_accuracy": {"correct": 1, "n": 1},
            "action_mismatches": [], "x_extra_false_positives": 0, "t": {"detectMs": {"median": 0, "p95": 1}}}


class TotalsTest(unittest.TestCase):
    def test_대조와_변형을_나눠_합친다(self):
        res = result({"control": {"gold": 4, "tp": 4, "full": 4}, "a": {"gold": 6, "tp": 3, "full": 2}, "b": {"gold": 4, "tp": 4, "full": 4}})
        self.assertEqual(nr.totals(res, control=False), (10, 7, 6))
        self.assertEqual(nr.totals(res, control=True), (4, 4, 4))

    def test_비율_표시(self):
        self.assertEqual(nr.pct(1, 4), "25.0%")
        self.assertEqual(nr.pct(0, 0), "-")
        self.assertTrue(nr.pct_ci(5, 10).startswith("50.0% ("))


class TransformTableTest(unittest.TestCase):
    def test_대조가_없는_세트도_표를_만든다(self):
        d = Path(tempfile.mkdtemp())
        for tag, tp in (("old", 1), ("new", 3)):
            (d / f"{tag}_bypass_v3.json").write_text(json.dumps(result({"x": {"gold": 4, "tp": tp, "full": tp}})), encoding="utf-8")
        table = nr.transform_table(d, "bypass_v3")
        self.assertIn("| x | 4 | 1 (25.0%) | 3 (75.0%) | 3 |", table)
        self.assertNotIn("대조", table)


if __name__ == "__main__":
    unittest.main()
