# -*- coding: utf-8 -*-
"""Z 3차 판정 도구 시험(명세 AC-10.8a~c). 서버와 키가 필요 없다."""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3_sheet as z3  # noqa: E402

ITEMS = ["PHONE", "EMAIL", "CARD", "RRN"]


def fake_data(n_per_item=10):
    records, collected = [], []
    for item in ITEMS:
        for k in range(n_per_item):
            i = f"{item.lower()}-{k:03d}"
            records.append({"id": i, "spans": [{"item": item}]})
            collected.append({"id": i, "request": f"요청 {i}", "answer_a": f"원문 답 {i}", "answer_b": f"가림 답 {i}"})
    return records, collected


class SampleTest(unittest.TestCase):
    def test_항목별로_고르게_뽑고_제외_목록은_뺀다(self):
        records, collected = fake_data()
        exclude = {c["id"] for c in collected[:5]}
        picked = z3.pick_pairs(records, collected, exclude, count=8, seed="s")
        self.assertEqual(len(picked), 8)
        self.assertFalse({p["id"] for p in picked} & exclude)
        items = [next(r for r in records if r["id"] == p["id"])["spans"][0]["item"] for p in picked]
        self.assertEqual({items.count(i) for i in ITEMS}, {2})

    def test_같은_seed는_같은_표본(self):
        records, collected = fake_data()
        a = z3.pick_pairs(records, collected, set(), 8, "s")
        b = z3.pick_pairs(records, collected, set(), 8, "s")
        c = z3.pick_pairs(records, collected, set(), 8, "t")
        self.assertEqual(a, b)
        self.assertNotEqual([p["id"] for p in a], [p["id"] for p in c])

    def test_답_B가_없는_행은_뽑지_않는다(self):
        records, collected = fake_data(3)
        collected[0]["answer_b"] = None
        ids = {p["id"] for p in z3.pick_pairs(records, collected, set(), 12, "s")}
        self.assertNotIn(collected[0]["id"], ids)


class SheetTest(unittest.TestCase):
    def setUp(self):
        records, collected = fake_data()
        self.pairs = z3.pick_pairs(records, collected, set(), 12, "s")
        self.tasks, self.key = z3.blind_tasks(self.pairs, seed="s")

    def test_양쪽에_가림_답이_섞이고_대응표가_맞다(self):
        self.assertEqual(set(self.key.values()), {"left", "right"})
        for t in self.tasks:
            b_side = self.key[t["id"]]
            self.assertEqual(t[b_side], next(p["answer_b"] for p in self.pairs if p["id"] == t["id"]))
            other = "left" if b_side == "right" else "right"
            self.assertEqual(t[other], next(p["answer_a"] for p in self.pairs if p["id"] == t["id"]))

    def test_화면에_대응표나_어느_쪽이_가림인지가_없다(self):
        page = z3.render_html(self.tasks)
        self.assertNotIn("answer_a", page)
        self.assertNotIn("answer_b", page)
        self.assertNotIn('"key"', page)
        for t in self.tasks:
            self.assertIn(t["request"], page)
        self.assertEqual(page.count("</script>"), 1)

    def test_스크립트_종료_태그가_답에_있어도_화면이_깨지지_않는다(self):
        tasks = [{"id": "x", "request": "r", "left": "a </script><b>", "right": "c"}]
        self.assertEqual(z3.render_html(tasks).count("</script>"), 1)


def row(i, q1, left, right, better):
    return {"id": i, "q1": q1, "left": left, "right": right, "better": better}


class SummaryTest(unittest.TestCase):
    def test_가림_답이_더_나쁜_쌍과_분포를_센다(self):
        key = {"p1": "left", "p2": "right", "p3": "left", "p4": "right"}
        rows = [
            row("p1", "있음", "×", "◎", "오른쪽"),   # B가 왼쪽: B가 더 나쁨(A가 오른쪽에서 더 좋음)
            row("p2", "있음", "◎", "◎", "비슷함"),
            row("p3", "빠짐", "○", "×", "왼쪽"),     # B가 왼쪽에서 더 좋음
            row("p4", "빠짐", "◎", "○", "왼쪽"),     # B가 오른쪽, 왼쪽(A)이 더 좋음: B가 더 나쁨
        ]
        s = z3.summarize(rows, key)
        self.assertEqual(s["n"], 4)
        self.assertEqual(s["b_worse"], 2)
        self.assertEqual(s["b_better"], 1)
        self.assertEqual(s["same"], 1)
        self.assertEqual(s["grades"]["A"], {"◎": 3, "○": 0, "×": 1})
        self.assertEqual(s["grades"]["B"], {"◎": 1, "○": 2, "×": 1})
        self.assertEqual(s["by_q1"]["빠짐"]["n"], 2)
        self.assertEqual(s["by_q1"]["빠짐"]["b_worse"], 1)
        self.assertEqual(len(s["b_worse_ci"]), 2)

    def test_빠진_판정은_세지_않고_알린다(self):
        key = {"p1": "left", "p2": "left"}
        s = z3.summarize([row("p1", "있음", "◎", "◎", "비슷함"), row("p2", "", "◎", "", "")], key)
        self.assertEqual(s["n"], 1)
        self.assertEqual(s["incomplete"], ["p2"])


if __name__ == "__main__":
    unittest.main()
