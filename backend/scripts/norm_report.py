# -*- coding: utf-8 -*-
"""
입력 정규화 비교 측정의 표를 만든다(명세 AC-13.11). `docs/eval/results/norm_v2/`의 이전 코드(old_*)와 새 코드(new_*) 결과를
읽어 마크다운 표를 출력한다. 서버가 필요 없다.

  python norm_report.py --dir ../../docs/eval/results/norm_v2 --out report_tables.md
"""
import argparse
import json
import sys
from pathlib import Path

import measure

SETS = ["bypass_v2", "bypass_v3"]


def load(directory, tag, name):
    return json.loads((Path(directory) / f"{tag}_{name}.json").read_text(encoding="utf-8"))


def pct(k, n):
    return "-" if n == 0 else f"{k / n * 100:.1f}%"


def pct_ci(k, n):
    if n == 0:
        return "-"
    lo, hi = measure.wilson(k, n)
    return f"{k / n * 100:.1f}% ({lo * 100:.0f}~{hi * 100:.0f}%)"


def totals(res, control):
    """대조 문장만(control=True) 또는 변형 문장만 합친 (정답 수, 탐지, 완전히 덮음)."""
    g = tp = full = 0
    for name, v in res["by_transform"].items():
        if (name == "control") == control:
            g += v["gold"]
            tp += v["tp"]
            full += v["full"]
    return g, tp, full


def summary_table(directory):
    rows = ["| 세트 | 구분 | 정답 수 | 이전 코드 탐지 | 새 코드 탐지 | 이전 완전히 덮음 | 새 완전히 덮음 | 새 코드 누출 |",
            "|---|---|---|---|---|---|---|---|"]
    for name in SETS:
        old, new = load(directory, "old", name), load(directory, "new", name)
        for label, control in (("변형", False), ("대조(변형 없음)", True)):
            g, otp, ofull = totals(old, control)
            _, ntp, nfull = totals(new, control)
            if g == 0:
                continue
            rows.append(f"| {name} | {label} | {g} | {pct_ci(otp, g)} | {pct_ci(ntp, g)} | {pct(ofull, g)} | {pct(nfull, g)} | {g - nfull} |")
    return "\n".join(rows)


def transform_table(directory, name):
    old, new = load(directory, "old", name), load(directory, "new", name)
    rows = [f"| 변형 유형 | 정답 수 | 이전 코드 탐지 | 새 코드 탐지 | 새 완전히 덮음 |", "|---|---|---|---|---|"]
    names = sorted((n for n in new["by_transform"] if n != "control"),
                   key=lambda n: -(new["by_transform"][n]["tp"] / new["by_transform"][n]["gold"]))
    for n in (["control"] if "control" in new["by_transform"] else []) + names:
        o, w = old["by_transform"][n], new["by_transform"][n]
        label = "대조(변형 없음)" if n == "control" else n
        rows.append(f"| {label} | {w['gold']} | {o['tp']} ({pct(o['tp'], o['gold'])}) | {w['tp']} ({pct(w['tp'], w['gold'])}) | {w['full']} |")
    return "\n".join(rows)


def benign_section(directory, dataset_path):
    old, new = load(directory, "old", "benign_v1"), load(directory, "new", "benign_v1")
    texts = {}
    for line in Path(dataset_path).read_text(encoding="utf-8").splitlines():
        if line.strip():
            r = json.loads(line)
            texts[r["id"]] = (r.get("group"), r["text"])
    lines = [f"정상 유사 문장 {new['y']['n']}개. 오탐(가림이나 차단): 이전 코드 {old['y']['false_positives']}개, "
             f"새 코드 {new['y']['false_positives']}개 (새 코드 {pct_ci(new['y']['false_positives'], new['y']['n'])})."]
    old_ids = {m["id"] for m in old["misses"]}
    new_hits = [m for m in new["misses"] if m["type"] == "FP_normal"]
    if new_hits:
        lines += ["", "| id | 묶음 | 항목 | 처리 | 이전 코드에서도 오탐 | 문장 |", "|---|---|---|---|---|---|"]
        seen = set()
        for m in new_hits:
            if m["id"] in seen:
                continue
            seen.add(m["id"])
            group, text = texts[m["id"]]
            lines.append(f"| {m['id']} | {group} | {m['item']} | {m['action']} | {'예' if m['id'] in old_ids else '아니오'} | {text} |")
    return "\n".join(lines)


def eval_section(directory):
    old, new = load(directory, "old", "eval_v1"), load(directory, "new", "eval_v1")
    same = all(old[k] == new[k] for k in ("x", "x_by_item", "y", "action_accuracy", "action_mismatches", "x_extra_false_positives"))
    t_old, t_new = old["t"]["detectMs"], new["t"]["detectMs"]
    return (f"평가 세트 v1 {new['n']}문장. X, Y, 처리 정확도, 틀린 문장 목록이 이전 코드와 {'모두 같다' if same else '다르다'}. "
            f"정상 오탐 {new['y']['false_positives']}/{new['y']['n']}, 처리 정확도 {new['action_accuracy']['correct']}/{new['action_accuracy']['n']}.\n\n"
            f"| 지연(ms) | 이전 코드 | 새 코드 |\n|---|---|---|\n| detectMs 중앙값 | {t_old['median']} | {t_new['median']} |\n"
            f"| detectMs p95 | {t_old['p95']} | {t_new['p95']} |")


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True)
    ap.add_argument("--set-dir", default=str(Path(__file__).resolve().parents[2] / "docs/eval/set"))
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    parts = ["## 요약", summary_table(args.dir)]
    for name in SETS:
        parts += [f"## 변형 유형별: {name}", transform_table(args.dir, name)]
    parts += ["## 정상 유사 세트", benign_section(args.dir, Path(args.set_dir) / "benign_v1.jsonl"),
              "## 평가 세트 v1 (회귀)", eval_section(args.dir)]
    Path(args.out).write_text("\n\n".join(parts) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
