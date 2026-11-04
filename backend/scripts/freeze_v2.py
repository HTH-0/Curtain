# -*- coding: utf-8 -*-
"""
측정 전 동결 도구(명세 AC-13.11). 탐지 코드, 회사 사전, 설정, 평가 세트와 새 세트들의 해시를 `freeze_v2.json`에 적고,
나중에 같은지 검증한다. 동결 뒤에는 결과를 보고 코드와 세트를 바꾸지 않는다.

  python freeze_v2.py freeze --out ../../docs/eval/set/freeze_v2.json     동결(탐지 코드에 커밋 안 한 변경이 있으면 거부)
  python freeze_v2.py verify --freeze ../../docs/eval/set/freeze_v2.json  지금 파일들이 동결 때와 같은지 확인

탐지 코드 해시의 계산 방식: `backend/src/main/java/com/promptgateway/detect/*.java`를 경로 순으로 정렬해 각 파일마다
`상대경로(/ 구분) + 줄바꿈 + 내용(CRLF는 LF로) + 줄바꿈`을 이어 붙인 바이트의 SHA-256이다.
(v1의 `detection_code_sha256`은 계산 방식이 기록되지 않아 재현하지 못했다. v1 해시와 직접 비교하지 않는다.)
"""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DETECT_DIR = "backend/src/main/java/com/promptgateway/detect"
SETS = {
    "eval_v1": "docs/eval/set/eval_v1.jsonl",
    "bypass_v2": "docs/eval/set/bypass_v2.jsonl",
    "bypass_v3_blind": "docs/eval/set/bypass_v3.jsonl",
    "benign_v1": "docs/eval/set/benign_v1.jsonl",
}
FILES = {
    "company_dictionary": "backend/src/main/resources/company/dictionary.yml",
    "application_yml": "backend/src/main/resources/application.yml",
}
METHOD = ("detect/*.java를 경로 순으로 정렬, 각 파일마다 상대경로(/ 구분) + \\n + 내용(CRLF를 LF로) + \\n 을 이어 붙인 바이트의 SHA-256")
COMPARE_COMMIT = "1066460"  # 정규화를 넣기 전 코드(비교 기준)


def sha256_file(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def code_hash(root=REPO, detect_dir=DETECT_DIR):
    base = Path(root)
    files = sorted((base / detect_dir).glob("*.java"), key=lambda p: p.relative_to(base).as_posix())
    h = hashlib.sha256()
    for p in files:
        rel = p.relative_to(base).as_posix()
        h.update(rel.encode("utf-8") + b"\n" + p.read_bytes().replace(b"\r\n", b"\n") + b"\n")
    return h.hexdigest()


def _git(*args):
    return subprocess.run(["git", *args], cwd=REPO, capture_output=True, text=True, check=True).stdout.strip()


def build_freeze(root=REPO, commit=None):
    base = Path(root)
    return {
        "frozen_at_commit": commit or _git("rev-parse", "HEAD"),
        "compare_commit": COMPARE_COMMIT,
        "detection_code_sha256": code_hash(base),
        "detection_code_method": METHOD,
        **{f"{k}_sha256": sha256_file(base / v) for k, v in FILES.items()},
        "sets": {k: {"path": v, "sha256": sha256_file(base / v)} for k, v in SETS.items()},
        "note": "측정 전 동결(명세 AC-13.11). 동결 뒤에는 결과를 보고 탐지 코드와 세트를 바꾸지 않는다.",
    }


def compare(frozen, current):
    """다른 항목의 이름 목록."""
    diffs = []
    for key in ("detection_code_sha256", "company_dictionary_sha256", "application_yml_sha256"):
        if frozen.get(key) != current.get(key):
            diffs.append(key)
    for name, v in frozen.get("sets", {}).items():
        if current["sets"].get(name, {}).get("sha256") != v["sha256"]:
            diffs.append(f"sets.{name}")
    return diffs


def main(argv=None):
    ap = argparse.ArgumentParser(description="측정 전 동결")
    sub = ap.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("freeze")
    f.add_argument("--out", required=True)
    v = sub.add_parser("verify")
    v.add_argument("--freeze", required=True)
    args = ap.parse_args(argv)
    if args.cmd == "freeze":
        dirty = _git("status", "--porcelain", "--", "backend/src/main")
        if dirty:
            print("탐지 코드나 설정에 커밋하지 않은 변경이 있어 동결하지 않습니다:\n" + dirty, file=sys.stderr)
            return 1
        data = build_freeze()
        Path(args.out).write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(f"동결 완료: 커밋 {data['frozen_at_commit'][:7]}, 탐지 코드 {data['detection_code_sha256'][:12]}")
        return 0
    frozen = json.loads(Path(args.freeze).read_text(encoding="utf-8"))
    diffs = compare(frozen, build_freeze(commit=frozen["frozen_at_commit"]))
    if diffs:
        print("동결과 다릅니다: " + ", ".join(diffs), file=sys.stderr)
        return 1
    print("동결과 같습니다")
    return 0


if __name__ == "__main__":
    sys.exit(main())
