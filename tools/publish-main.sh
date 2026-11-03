#!/usr/bin/env bash
# dev의 커밋된 상태에서 tools/main-whitelist.txt에 적힌 경로만 골라 main에 커밋 하나를 얹는다.
# 작업 폴더와 현재 브랜치를 바꾸지 않는다(임시 인덱스로 트리를 만든다). 푸시는 하지 않는다.
#
# 사용법: tools/publish-main.sh [--dry-run] [-m "커밋 메시지"]
set -euo pipefail

dry_run=0
message=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) dry_run=1 ;;
    -m) shift; message="${1:?-m 뒤에 메시지가 필요하다}" ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
  shift
done

cd "$(git rev-parse --show-toplevel)"
git rev-parse --verify -q refs/heads/dev >/dev/null || { echo "dev 브랜치가 없다" >&2; exit 1; }
git rev-parse --verify -q refs/heads/main >/dev/null || { echo "main 브랜치가 없다" >&2; exit 1; }

# 허용 목록을 읽는다. dev에 커밋된 목록을 쓴다.
specs=()
while IFS= read -r line; do
  line="${line%$'\r'}"
  case "$line" in ''|'#'*) continue ;; esac
  case "$line" in
    '!'*) specs+=(":(exclude)${line#!}") ;;
    *) specs+=("$line") ;;
  esac
done < <(git show dev:tools/main-whitelist.txt)
[ ${#specs[@]} -gt 0 ] || { echo "허용 목록이 비어 있다" >&2; exit 1; }

idx="$(mktemp -u)"
trap 'rm -f "$idx"' EXIT
export GIT_INDEX_FILE="$idx"

git read-tree dev
git ls-files -z | sort -z > "$idx.all"
git ls-files -z -- "${specs[@]}" | sort -z > "$idx.keep"
trap 'rm -f "$idx" "$idx.all" "$idx.keep"' EXIT

[ -s "$idx.keep" ] || { echo "허용 목록에 맞는 파일이 없다" >&2; exit 1; }
comm -z -23 "$idx.all" "$idx.keep" | git update-index --force-remove -z --stdin
tree="$(git write-tree)"
unset GIT_INDEX_FILE

kept="$(tr -cd '\0' < "$idx.keep" | wc -c | tr -d ' ')"
dev_short="$(git rev-parse --short dev)"
echo "dev($dev_short)에서 고른 파일: ${kept}개"

if [ "$tree" = "$(git rev-parse 'main^{tree}')" ]; then
  echo "main과 같다. 바꿀 것이 없다."
  exit 0
fi

echo "main과 달라지는 파일(A 추가, M 수정, D 삭제):"
git -c core.quotepath=false diff-tree -r --no-renames --name-status main "$tree" | sed 's/^/  /'

if [ "$dry_run" -eq 1 ]; then
  echo "(--dry-run: main을 바꾸지 않았다)"
  exit 0
fi

[ -n "$message" ] || message="공개용 갱신: dev $dev_short 기준"
commit="$(git commit-tree "$tree" -p main -m "$message")"
git update-ref -m "publish-main: $message" refs/heads/main "$commit"
echo "main을 ${commit:0:7}로 갱신했다. 푸시는 직접 한다: git push origin main"
