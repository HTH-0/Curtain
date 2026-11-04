# -*- coding: utf-8 -*-
"""블라인드 우회 세트 조립기 시험(명세 AC-13.9). 서버가 필요 없다."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_blind_set as bs  # noqa: E402


class InputTest(unittest.TestCase):
    def test_항목마다_값_5개_모두_60개(self):
        inputs = bs.make_inputs()
        self.assertEqual(len(inputs), 60)
        for item in bs.bb.PLAN:
            self.assertEqual(sum(1 for x in inputs if x["item"] == item), 5, item)

    def test_사전_항목은_사전_단어이고_같은_입력이_나온다(self):
        inputs = bs.make_inputs()
        words = {x["value"] for x in inputs if x["item"] == "CODENAME"}
        self.assertEqual(words, set(bs.be.COMPANY["CODENAME"]))
        self.assertEqual(inputs, bs.make_inputs())


class AssembleTest(unittest.TestCase):
    def setUp(self):
        self.inputs = [x for x in bs.make_inputs() if x["id"] in ("PHONE-1", "ACCOUNT-1", "CLASSIFIED-1")]
        self.phone = next(x for x in self.inputs if x["id"] == "PHONE-1")

    def doc(self, ident, *variants):
        return {"id": ident, "variants": [{"type": t, "text": s} for t, s in variants]}

    def test_정답_구간이_변형_문자열과_맞는다(self):
        records, dropped = bs.assemble(self.inputs, [
            self.doc("PHONE-1", ("문자 바꾸기", "공일공 사영사육 구오공일"), ("구분자와 공백", "010 4046 9501")),
            self.doc("ACCOUNT-1", ("구분자와 공백", "919 760 003067")),
            self.doc("CLASSIFIED-1", ("보이지 않는 문자", "대​외비")),
        ])
        self.assertEqual(dropped, [])
        self.assertEqual(len(records), 4)
        for r in records:
            s = r["spans"][0]
            self.assertEqual(r["text"][s["start"]:s["end"]], r["value_sent"], r["id"])
        by_item = {r["item"]: r for r in records}
        self.assertEqual(by_item["PHONE"]["expected_action"], "MASK")
        self.assertEqual(by_item["CLASSIFIED"]["expected_action"], "BLOCK")
        self.assertIn(self.inputs[1]["bank"], by_item["ACCOUNT"]["text"])

    def test_문제_있는_변형은_버리고_이유를_남긴다(self):
        records, dropped = bs.assemble(self.inputs, [
            self.doc("PHONE-1",
                     ("문자 바꾸기", self.phone["value"]),            # 원래 값과 같음
                     ("구분자와 공백", ""),                           # 빈 변형
                     ("없는 유형", "010 4046 9501"),                  # 유형 목록에 없음
                     ("쪼개기", "010-40\n46-9501"),                   # 정상
                     ("쪼개기", "010-4046-\n9501"),                   # 같은 값에서 유형 중복
                     ("기타", "x" * 300)),                            # 너무 긴 변형
            self.doc("NOPE-1", ("기타", "a")),                        # 알 수 없는 id
        ])
        self.assertEqual(len(records), 1)
        reasons = [r for _, r in dropped]
        self.assertEqual(len(dropped), 6)
        self.assertTrue(any("같은 값에서 유형 중복" in r for r in reasons))
        self.assertTrue(any("알 수 없는 id" in r for r in reasons))

    def test_값마다_변형은_4개까지(self):
        types = bs.TYPES[:5]
        records, _ = bs.assemble(self.inputs, [self.doc("PHONE-1", *[(t, f"변형{n}") for n, t in enumerate(types)])])
        self.assertEqual(len(records), bs.VARIANTS_PER_VALUE)


if __name__ == "__main__":
    unittest.main()
