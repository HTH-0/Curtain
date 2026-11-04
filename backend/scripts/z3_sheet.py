# -*- coding: utf-8 -*-
"""
Z 3차 사람 판정 도구(명세 AC-10.8a~c, 기준은 docs/eval/z3_rubric.md).

  python z3_sheet.py make --out-dir ../../docs/eval/results/z3        판정 화면(HTML)과 대응표를 만든다
  python z3_sheet.py summarize --filled <CSV> --key <대응표>           판정 결과를 집계한다

판정 화면에는 어느 쪽이 가림 처리 답(B)인지 없다. 대응표(human_key.json)는 판정이 끝나기 전에 열지 않는다.
Jev 점수는 쓰지 않는다. 입력은 가짜 데이터의 답뿐이다.
"""
import argparse
import csv
import json
import random
import sys
from pathlib import Path

import measure

REPO = Path(__file__).resolve().parents[2]
GRADES = ("◎", "○", "×")
Q1_VALUES = ("있음", "빠짐")
BETTER_VALUES = ("왼쪽", "오른쪽", "비슷함")


def _read_jsonl(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def pick_pairs(records, collected, exclude, count, seed):
    """항목별로 고르게 고정 seed로 뽑는다. 제외 목록과 답 B가 없는 행은 뺀다(AC-10.8b)."""
    by_id = {r["id"]: r for r in records}
    rows = [r for r in collected if r.get("answer_b") is not None and r["id"] in by_id and r["id"] not in exclude]
    rng = random.Random(seed)
    groups = {}
    for r in sorted(rows, key=lambda r: r["id"]):
        groups.setdefault(by_id[r["id"]]["spans"][0]["item"], []).append(r)
    for g in groups.values():
        rng.shuffle(g)
    picked = []
    order = sorted(groups)
    while len(picked) < count and any(groups.values()):
        for item in order:
            if groups[item] and len(picked) < count:
                picked.append(groups[item].pop())
    rng.shuffle(picked)
    return [{"id": r["id"], "request": r["request"], "answer_a": r["answer_a"], "answer_b": r["answer_b"]} for r in picked]


def blind_tasks(pairs, seed):
    """가림 답(B)을 왼쪽이나 오른쪽에 무작위로 놓는다. 화면용 목록과 대응표(B가 있는 쪽)를 돌려준다."""
    rng = random.Random(f"{seed}:blind")
    tasks, key = [], {}
    for p in pairs:
        b_side = rng.choice(["left", "right"])
        a_side = "right" if b_side == "left" else "left"
        tasks.append({"id": p["id"], "request": p["request"], b_side: p["answer_b"], a_side: p["answer_a"]})
        key[p["id"]] = b_side
    return tasks, key


PAGE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Z 3차 사람 판정</title>
<style>
  :root { --bg:#f6f7f9; --fg:#1d2330; --card:#fff; --line:#d9dde5; --accent:#2f5bd8; --on:#2f5bd8; }
  @media (prefers-color-scheme: dark) {
    :root { --bg:#14171d; --fg:#e6e9ef; --card:#1d222b; --line:#343b48; --accent:#7b9cff; --on:#7b9cff; }
  }
  * { box-sizing:border-box; }
  body { margin:0; background:var(--bg); color:var(--fg); font:16px/1.6 system-ui,"Malgun Gothic",sans-serif; }
  header { position:sticky; top:0; z-index:2; background:var(--card); border-bottom:1px solid var(--line); padding:10px 16px; display:flex; gap:12px; align-items:center; flex-wrap:wrap; }
  .bar { flex:1; min-width:120px; height:8px; background:var(--line); border-radius:4px; overflow:hidden; }
  .bar > div { height:100%; background:var(--accent); width:0; }
  main { max-width:1200px; margin:0 auto; padding:16px; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:14px 16px; margin-bottom:14px; }
  .label { font-size:13px; opacity:.7; margin-bottom:4px; }
  .req { white-space:pre-wrap; font-weight:600; }
  .answers { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
  @media (max-width:800px) { .answers { grid-template-columns:1fr; } }
  .ans { white-space:pre-wrap; word-break:break-word; max-height:60vh; overflow:auto; border:1px solid var(--line); border-radius:6px; padding:10px; margin:6px 0 10px; font-size:15px; }
  .row { display:flex; gap:8px; flex-wrap:wrap; align-items:center; margin:6px 0; }
  button { font:inherit; padding:8px 16px; border:1px solid var(--line); border-radius:6px; background:var(--card); color:var(--fg); cursor:pointer; }
  button:focus-visible { outline:2px solid var(--accent); outline-offset:2px; }
  button.on { background:var(--on); border-color:var(--on); color:#fff; }
  .nav { display:flex; gap:8px; justify-content:space-between; flex-wrap:wrap; align-items:center; }
  .hint { font-size:13px; opacity:.8; }
  details { margin-bottom:14px; }
  summary { cursor:pointer; }
</style>
</head>
<body>
<header>
  <strong id="count"></strong>
  <div class="bar"><div id="fill"></div></div>
  <button id="export">CSV 저장</button>
</header>
<main>
  <details class="card">
    <summary>판정 기준 다시 보기</summary>
    <p><b>질문 1.</b> 요청이 가리키는 자료(코드, 로그, 문서 등)가 요청 안에 있으면 "있음", 없으면 "빠짐".</p>
    <p><b>질문 2.</b> ◎ 바로 씀(요청한 일을 구체적으로 해냄) / ○ 고쳐 씀(방향은 맞으나 채우거나 손봐야 함, 빠진 내용을 정확히 되묻는 답 포함) / × 못 씀(지어냄, 다른 일을 함, 끊김, 가명이 풀리지 않음, 내용이 빠졌는데 아는 척함).</p>
    <p><b>질문 3.</b> 어느 쪽이 더 쓸모 있는가: 왼쪽, 오른쪽, 비슷함. 코드의 정확성은 검증하지 않고, 요청에 없는 값을 지어낸 것이 보이면 ×.</p>
  </details>
  <div id="view"></div>
</main>
<script>
const TASKS = __TASKS__;
const KEY = "z3-human-judge-v1";
const Q1 = ["있음", "빠짐"], GR = ["◎", "○", "×"], BT = ["왼쪽", "오른쪽", "비슷함"];
let state = {}, idx = 0;
try { state = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) { state = {}; }
function save() { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {} }
function done(t) { const s = state[t.id] || {}; return s.q1 && s.left && s.right && s.better; }
function el(tag, cls, text) { const e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; }
function group(label, options, current, onPick) {
  const row = el("div", "row"); row.appendChild(el("span", "", label));
  options.forEach(o => { const b = el("button", o === current ? "on" : "", o); b.type = "button"; b.onclick = () => onPick(o); row.appendChild(b); });
  return row;
}
function render() {
  const t = TASKS[idx], s = state[t.id] = state[t.id] || {};
  const n = TASKS.filter(done).length;
  document.getElementById("count").textContent = (idx + 1) + " / " + TASKS.length + " (완료 " + n + ")";
  document.getElementById("fill").style.width = (n / TASKS.length * 100) + "%";
  const v = document.getElementById("view"); v.textContent = "";
  const req = el("div", "card"); req.appendChild(el("div", "label", "요청")); req.appendChild(el("div", "req", t.request)); v.appendChild(req);
  const grid = el("div", "answers");
  [["left", "왼쪽 답"], ["right", "오른쪽 답"]].forEach(([side, name]) => {
    const c = el("div", "card"); c.appendChild(el("div", "label", name)); c.appendChild(el("div", "ans", t[side]));
    c.appendChild(group("쓸모", GR, s[side], o => { s[side] = o; save(); render(); }));
    grid.appendChild(c);
  });
  v.appendChild(grid);
  const q = el("div", "card");
  q.appendChild(group("질문 1. 요청에 필요한 내용이", Q1, s.q1, o => { s.q1 = o; save(); render(); }));
  q.appendChild(group("질문 3. 더 쓸모 있는 쪽", BT, s.better, o => { s.better = o; save(); render(); }));
  v.appendChild(q);
  const nav = el("div", "nav");
  const prev = el("button", "", "이전"); prev.type = "button"; prev.disabled = idx === 0; prev.onclick = () => { idx--; render(); window.scrollTo(0, 0); };
  const next = el("button", "", idx === TASKS.length - 1 ? "끝" : "다음"); next.type = "button"; next.disabled = idx === TASKS.length - 1; next.onclick = () => { idx++; render(); window.scrollTo(0, 0); };
  nav.appendChild(prev); nav.appendChild(el("span", "hint", "선택은 이 브라우저에 자동 저장됩니다. 끝나면 위의 CSV 저장을 누르세요.")); nav.appendChild(next);
  v.appendChild(nav);
}
document.getElementById("export").onclick = () => {
  const lines = ["id,q1,left,right,better"];
  TASKS.forEach(t => { const s = state[t.id] || {}; lines.push([t.id, s.q1 || "", s.left || "", s.right || "", s.better || ""].join(",")); });
  const blob = new Blob(["\\ufeff" + lines.join("\\n") + "\\n"], { type: "text/csv;charset=utf-8" });
  const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = "human_sheet_z3_filled.csv"; a.click();
};
const first = TASKS.findIndex(t => !done(t)); idx = first < 0 ? 0 : first;
render();
</script>
</body>
</html>
"""


def render_html(tasks):
    """판정 화면. 답에 </script>가 있어도 깨지지 않게 '</'를 이스케이프한다."""
    data = json.dumps(tasks, ensure_ascii=False).replace("</", "<\\/")
    return PAGE.replace("__TASKS__", data)


def summarize(rows, key):
    """판정 결과를 집계한다(AC-10.8c). 빠진 판정이 있는 쌍은 세지 않고 id를 알린다."""
    out = {"n": 0, "b_worse": 0, "b_better": 0, "same": 0, "incomplete": [],
           "grades": {"A": dict.fromkeys(GRADES, 0), "B": dict.fromkeys(GRADES, 0)},
           "by_q1": {q: {"n": 0, "b_worse": 0, "b_better": 0, "same": 0} for q in Q1_VALUES},
           "a_usable_b_unusable": 0, "a_unusable_b_usable": 0}
    for r in rows:
        ok = (r.get("q1") in Q1_VALUES and r.get("left") in GRADES and r.get("right") in GRADES
              and r.get("better") in BETTER_VALUES and r.get("id") in key)
        if not ok:
            out["incomplete"].append(r.get("id"))
            continue
        b_side = key[r["id"]]
        a_side = "right" if b_side == "left" else "left"
        grade_b, grade_a = r[b_side], r[a_side]
        if r["better"] == "비슷함":
            outcome = "same"
        elif r["better"] == ("왼쪽" if b_side == "left" else "오른쪽"):
            outcome = "b_better"
        else:
            outcome = "b_worse"
        out["n"] += 1
        out[outcome] += 1
        out["by_q1"][r["q1"]]["n"] += 1
        out["by_q1"][r["q1"]][outcome] += 1
        out["grades"]["A"][grade_a] += 1
        out["grades"]["B"][grade_b] += 1
        if grade_a != "×" and grade_b == "×":
            out["a_usable_b_unusable"] += 1
        if grade_a == "×" and grade_b != "×":
            out["a_unusable_b_usable"] += 1
    out["b_worse_ci"] = list(measure.wilson(out["b_worse"], out["n"]))
    return out


def read_filled(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def cmd_make(args):
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    records = _read_jsonl(args.eval_set)
    collected = _read_jsonl(args.collected)
    exclude = set(json.loads(Path(args.exclude_key).read_text(encoding="utf-8")))
    pairs = pick_pairs(records, collected, exclude, args.count, args.seed)
    tasks, key = blind_tasks(pairs, args.seed)
    (out_dir / "human_judge.html").write_text(render_html(tasks), encoding="utf-8")
    (out_dir / "human_key.json").write_text(json.dumps(key, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    by_id = {r["id"]: r for r in records}
    counts = {}
    for p in pairs:
        item = by_id[p["id"]]["spans"][0]["item"]
        counts[item] = counts.get(item, 0) + 1
    print(f"판정 화면 {len(tasks)}쌍: {out_dir / 'human_judge.html'}")
    print(f"항목별 쌍 수: {counts}")
    print(f"대응표(판정이 끝나기 전에 열지 않는다): {out_dir / 'human_key.json'}")
    return 0


def cmd_summarize(args):
    key = json.loads(Path(args.key).read_text(encoding="utf-8"))
    result = summarize(read_filled(args.filled), key)
    text = json.dumps(result, ensure_ascii=False, indent=1)
    if args.out:
        Path(args.out).write_text(text + "\n", encoding="utf-8")
    print(text)
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description="Z 3차 사람 판정")
    sub = ap.add_subparsers(dest="cmd", required=True)
    m = sub.add_parser("make")
    m.add_argument("--out-dir", default=str(REPO / "docs/eval/results/z3"))
    m.add_argument("--eval-set", default=str(REPO / "docs/eval/set/eval_v1.jsonl"))
    m.add_argument("--collected", default=str(REPO / "docs/eval/results/z/collected.jsonl"))
    m.add_argument("--exclude-key", default=str(REPO / "docs/eval/results/z/human_key.json"))
    m.add_argument("--count", type=int, default=24)
    m.add_argument("--seed", default="curtain-z3-v1")
    s = sub.add_parser("summarize")
    s.add_argument("--filled", required=True)
    s.add_argument("--key", required=True)
    s.add_argument("--out")
    args = ap.parse_args(argv)
    return cmd_make(args) if args.cmd == "make" else cmd_summarize(args)


if __name__ == "__main__":
    sys.exit(main())
