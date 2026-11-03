@AGENTS.md

# 이 저장소에서 시작할 때

0. 브랜치는 둘이다(2026-11-03 구조 변경, 클라우드 작업을 쓰지 않는다). `dev`는 작업용이고 모든 문서와 기록이 들어 있다. `main`은 공개용이고 실행에 필요한 코드와 핵심 문서만 있으며 Render가 이 브랜치를 배포한다.
   - **모든 작업은 `dev`에서 한다.** `main`에서 직접 고치지 않는다(다음 반영 때 `dev` 내용으로 덮어쓴다). `main`과 `dev`를 `git merge`로 합치지 않는다(`main`에서 뺀 문서가 되살아난다).
   - 시작할 때 `git fetch origin --prune` 후 `git switch dev`, `git pull`로 최신 상태를 받는다. `dev`가 원격과 일치하는지 확인하고, `git branch -r`에 `dev`, `main` 외의 브랜치가 보이면 그 브랜치의 `docs/handoff.md`와 비교해서 사용자에게 알린다.
   - `main` 반영은 `bash tools/publish-main.sh --dry-run`으로 바뀌는 파일 목록을 보고 사용자의 승인을 받은 뒤 실행한다. 올릴 경로는 `tools/main-whitelist.txt`가 정한다(`dev`에 커밋한 것을 쓴다). 푸시는 따로 사용자에게 확인한다. `main`을 푸시하면 공개 사이트가 다시 배포된다.
1. 먼저 [docs/handoff.md](docs/handoff.md)를 읽는다. 현재 상태, 사용자 결정 대기 목록, 다음 작업이 있다.
2. 작업 규칙(한국어 응답, 계획 후 승인, 작업 스텝 보고)은 위에서 가져온 AGENTS.md를 따른다.
3. 결정이 생기면 그 자리에서 DECISIONS.md에 기록한다.
