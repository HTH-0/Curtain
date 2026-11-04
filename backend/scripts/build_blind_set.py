# -*- coding: utf-8 -*-
"""
블라인드 우회 세트 v3 생성기(명세 AC-13.9). 탐지 규칙과 이전 보고서를 보지 못하는 별도 서브에이전트가 가짜 값에 변형을 가하고,
이 도구가 그 결과를 문장과 정답 구간으로 조립한다. 규칙을 만든 쪽이 모르는 변형으로 일반화 정도를 보려는 것이다.

  python build_blind_set.py input --out <입력.json>          서브에이전트에게 줄 가짜 값 목록을 만든다
  python build_blind_set.py assemble --variants <변형.json> --out <세트.jsonl> [--avoid 세트.jsonl ...]

실제 개인정보는 쓰지 않는다. 같은 seed와 같은 변형 파일은 같은 세트를 만든다.
"""
import argparse
import json
import sys

import build_bypass_set as bb
import build_eval_set as be

SEED = "curtain-bypass-v3"
VALUES_PER_ITEM = 5
VARIANTS_PER_VALUE = 4
MAX_VARIANT_LENGTH = 240

# 서브에이전트가 고르는 변형 유형. 규칙 이름이 아니라 일반적인 분류이다.
TYPES = ["문자 바꾸기", "구분자와 공백", "보이지 않는 문자", "쪼개기", "표기 방식", "인코딩", "기타"]

ITEM_DESCRIPTIONS = {
    "PHONE": "한국 휴대전화번호", "RRN": "주민등록번호(가짜)", "FRN": "외국인등록번호(가짜)", "CARD": "신용카드 번호(가짜)",
    "EMAIL": "이메일 주소", "ACCOUNT": "은행 계좌번호(가짜)", "API_KEY": "API 키(가짜)", "JWT": "JWT 토큰(가짜)",
    "CONN_STRING": "비밀번호가 든 데이터베이스 접속 문자열(가짜)", "CODENAME": "회사 내부 프로젝트 코드명",
    "CLIENT": "고객사 이름", "CLASSIFIED": "문서 보안 등급 표시",
}
CATEGORY = {item: category for item, (category, _) in bb.PLAN.items()}


def make_inputs():
    """항목마다 가짜 값 5개(사전 항목은 사전 단어 5개)."""
    out = []
    for item in bb.PLAN:
        if item in bb.DICTIONARY_ITEMS:
            values = [(w, None) for w in be.COMPANY[item]][:VALUES_PER_ITEM]
        else:
            fake = be.rng_for(f"{SEED}:input:{item}")
            values = []
            seen = set()
            while len(values) < VALUES_PER_ITEM:
                v, bank = bb._base_value(item, fake)
                if v not in seen:
                    seen.add(v)
                    values.append((v, bank))
        for n, (v, bank) in enumerate(values, 1):
            out.append({"id": f"{item}-{n}", "item": item, "description": ITEM_DESCRIPTIONS[item], "value": v, "bank": bank})
    return out


def assemble(inputs, variants_doc):
    """서브에이전트의 변형을 문장과 정답 구간으로 조립한다. 문제가 있는 변형은 버리고 버린 이유를 함께 돌려준다."""
    by_id = {x["id"]: x for x in inputs}
    records, dropped = [], []
    for entry in variants_doc:
        src = by_id.get(entry.get("id"))
        if src is None:
            dropped.append((entry.get("id"), "알 수 없는 id"))
            continue
        seen_sent, seen_types = {src["value"]}, set()
        for k, var in enumerate(entry.get("variants", []), 1):
            sent, vtype = var.get("text"), var.get("type")
            if not isinstance(sent, str) or not sent.strip():
                dropped.append((entry["id"], "빈 변형"))
            elif sent in seen_sent:
                dropped.append((entry["id"], "원래 값과 같거나 중복"))
            elif len(sent) > MAX_VARIANT_LENGTH:
                dropped.append((entry["id"], "너무 긴 변형"))
            elif vtype not in TYPES:
                dropped.append((entry["id"], f"유형 목록에 없음: {vtype}"))
            elif vtype in seen_types:
                dropped.append((entry["id"], "같은 값에서 유형 중복"))
            else:
                seen_sent.add(sent)
                seen_types.add(vtype)
                records.append(_record(src, sent, vtype, k))
            if len(seen_types) >= VARIANTS_PER_VALUE:
                break
    return records, dropped


def _record(src, sent, vtype, k):
    item = src["item"]
    fake = be.rng_for(f"{SEED}:carrier:{src['id']}:{k}")
    carriers = bb.ACCOUNT_CARRIERS if item == "ACCOUNT" else bb.CARRIERS
    carrier = fake.r.choice(carriers).replace("{B}", src.get("bank") or "")
    prefix, suffix = carrier.split("{V}")
    span = {"category": CATEGORY[item], "item": item, "start": len(prefix), "end": len(prefix) + len(sent)}
    return {"id": f"blind-{src['id'].lower()}-{k}", "text": prefix + sent + suffix, "kind": "sensitive",
            "expected_action": be.expected_action([span]), "spans": [span], "item": item, "transform": vtype,
            "value_original": src["value"], "value_sent": sent}


def main(argv=None):
    ap = argparse.ArgumentParser(description="블라인드 우회 세트 v3")
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("input")
    a.add_argument("--out", required=True)
    b = sub.add_parser("assemble")
    b.add_argument("--inputs", required=True)
    b.add_argument("--variants", required=True)
    b.add_argument("--out", required=True)
    b.add_argument("--avoid", action="append", default=[])
    args = ap.parse_args(argv)
    if args.cmd == "input":
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump({"types": TYPES, "variants_per_value": VARIANTS_PER_VALUE, "values": make_inputs()}, f, ensure_ascii=False, indent=1)
        print(f"입력 {len(make_inputs())}개를 썼습니다")
        return 0
    with open(args.inputs, encoding="utf-8") as f:
        inputs = json.load(f)["values"]
    with open(args.variants, encoding="utf-8") as f:
        variants_doc = json.load(f)
    records, dropped = assemble(inputs, variants_doc)
    for path in args.avoid:
        with open(path, encoding="utf-8") as f:
            other = [json.loads(line) for line in f if line.strip()]
        dup = bb.overlap_with(records, other)
        if dup:
            print(f"{path}와 겹치는 값 {len(dup)}개", file=sys.stderr)
            return 1
    digest = be.write_dataset(records, args.out)
    print(f"만든 문장 {len(records)}개, 버린 변형 {len(dropped)}개, 해시 {digest}")
    for ident, reason in dropped[:20]:
        print("  버림:", ident, reason)
    return 0


if __name__ == "__main__":
    sys.exit(main())
