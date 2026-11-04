# 이어서 작업하기

다른 컴퓨터나 새 세션에서 이어서 작업할 때 가장 먼저 읽는 문서이다. 세션을 마칠 때마다 갱신한다. 무엇을 왜 했는지는 [DECISIONS.md](../DECISIONS.md)와 [worklog.md](worklog.md)에, 주제를 바꾸기 전(HR 챗봇) 자료는 [archive/](archive/README.md)에 있다.

기준일: 2026-11-04. 제출할 수 있는 상태이다(마감 11-06). 11-03까지의 내용은 GitHub와 공개 사이트에 반영했다. 11-04의 입력 정규화(FR-13)는 `dev`에 로컬 커밋만 했고 푸시와 `main` 반영은 아직 하지 않았다. 같은 날 브랜치를 `dev`(작업용)와 `main`(공개용)으로 나눴다. 안전용 태그는 `before-cleanup`(`e98e369`, 나누기 전 상태)이다.

## 1. 한눈에 보기

- **프로젝트:** Curtain(이전 이름 Deskguard). 회사 직원이 ChatGPT 같은 외부 AI에 보내는 문장을 나가기 전에 검사하는 정보 유출 방지 게이트웨이와 사내 AI 포털이다. 왜 만드는지는 [problem.md](problem.md)(최상위 문서)에 있다.
- **처리:** 개인정보(D1), 자격증명(D2), 회사 기밀(D3)을 게이트웨이 안의 코드로 탐지하고 허용, 가림(가명으로 바꿔 보내고 답에서 되돌림), 경고, 차단으로 처리한다. 운영 경로는 외부 판정 서비스를 부르지 않는다.
- **공개 시연:** <https://curtain-h8id.onrender.com/> (Render 무료 등급, Docker, 시연 모드, 서버 키 없음, `main`에 올리면 자동 배포). 방문자가 자기 Gemini 키를 넣으면 그 요청에만 실제 Gemini를 쓴다(FR-12).
- **저장소:** <https://github.com/HTH-0/Curtain> (2026-11-02 `Deskguard`에서 이름 변경, 공개 유지로 확정). 브랜치는 둘이다. `dev`는 작업용으로 모든 기록 문서를 포함하고, `main`은 공개용으로 실행에 필요한 코드와 핵심 문서만 둔다(README가 `dev`를 링크한다). 작업은 `dev`에서 하고, `main`은 `bash tools/publish-main.sh`로만 갱신한다(`CLAUDE.md` 0번).
- **개발 방식:** SDD와 TDD를 병행한다. 기획은 문제 정의 → 정책 → 명세 → 설계 → 작업 목록, 구현은 수용 기준 → 실패하는 시험 → 코드이다. 각 작업의 첫 스텝은 명세 점검이고, 컴파일 오류는 빨강으로 치지 않는다.
- **기술:** Spring Boot 4.1.1, Java 25 LTS, Maven, Python 3(측정 도구). 외부 AI는 Gemini. 화면은 정적 HTML 한 장(`backend/src/main/resources/static/index.html`)이고 외부 라이브러리와 외부 주소를 쓰지 않는다.

## 2. 지금 상태

| 영역 | 상태 |
|---|---|
| 게이트웨이(탐지, 결정, 가림, 복원, 로그) | 완료. 명세 FR-1~FR-7 |
| 포털 화면 | 완료. 손그림 크레용 화풍, 첫 화면 커튼 효과와 흐름도(스크롤마다 한 칸씩 켜지는 전화번호 변환 줄 포함), 포털(종류별 테두리 색의 예시 버튼 7개, 커튼 기록, 모드 표시, 답을 기다리는 말풍선), 내 키로 써 보기, 내 앱에 연결하기. 명세 FR-8(AC-8.4~8.12) |
| OpenAI 호환 입구(`/v1`) | 완료. Chatbox와 공식 OpenAI SDK로 확인. 명세 FR-11 |
| 내 Gemini 키로 쓰기, 공개 배포 | 완료. 키는 브라우저 탭에만, 서버는 저장하지 않음, Gemini에는 헤더로. `AIza`, `AQ.` 두 형식. 명세 FR-12 |
| 측정 | 완료. X·Y·T, 우회, Z 1차와 2차. 결과는 [eval/results/](eval/results/)와 README "측정 결과" |
| 입력 정규화(FR-13) | 완료(로컬 커밋, 푸시 전). 전각, 한글 숫자, 보이지 않는 문자, 구분자, 글자 띄어쓰기. 우회 탐지: 알려진 유형 26.2%에서 79.8%, 블라인드 12.1%에서 48.3%. 평가 세트 회귀 없음 |
| 시험 | Java 207건, Python 95건 통과(키 없이). 아래 4절의 명령 |

- **최근에 한 일(2026-11-04, 입력 정규화):** 탐지용 사본을 정규화(NFKC, 보이지 않는 문자 삭제, 한글 숫자 3글자 이상 변환, 한 글자씩 띄운 숫자 합치기)하고 원문과 사본 양쪽에서 찾아 합쳤다. 위치는 원문으로 되돌리므로 `Masker`는 바꾸지 않았다. 주민번호, 카드, 계좌 구분자와 사전어 글자 띄어쓰기를 넓혔고, 우체국 송장번호는 계좌로 보지 않게 했다. 측정은 세트를 만든 뒤 `freeze_v2.json`에 동결하고 이전 코드와 새 코드를 같은 세트로 쟀다(우회 v2 600문장, 서브에이전트가 만든 블라인드 v3 240문장, 정상 유사 52문장, 평가 세트 v1). 결과와 한계는 [eval/results/report_bypass_v2.md](eval/results/report_bypass_v2.md). 결정은 DECISIONS의 2026-11-04 항목 2개에 있다.
- **그 앞(2026-11-03 오후, 공개용 정리):** 브랜치를 `dev`와 `main`으로 나눴다. `main`에는 143개 파일(`backend/`, `Dockerfile`, README, 핵심 문서, 측정 보고서)만 두고, `AGENTS.md`, `CLAUDE.md`, `DECISIONS.md`, `docs/archive/`, handoff, worklog, tasks, Z 원자료는 `dev`에만 둔다. 학습노트, 코드 읽기, 외부 데이터 파일 2개를 저장소에서 제거했다(백업은 내 컴퓨터). README를 한다체 소개 글로 다시 썼고 사진 3장을 공개 사이트에서 다시 찍었다(`tools/shoot-screens.mjs`). `main` 후보로 Java 183건, Python 79건 통과와 끊어진 링크 0개를 확인한 뒤 `main` `ce5b5cc`를 푸시했다. 결정과 근거는 DECISIONS의 2026-11-03 "공개용 정리" 항목에 있다.
- **그 앞(2026-11-03 낮):** 흐름도 밑에 전화번호 변환 줄(`010-1111-2222 → [전화1] → [전화1] → [전화1] → 010-1111-2222`, 스크롤마다 한 칸씩, 3단계부터 "AI는 실제 전화번호가 어떤 건지 몰라요")과 흐름도의 차단 알약 삭제(AC-8.10 보완), 답을 기다리는 말풍선(AC-8.12), 화면 점검 반영(코드 안 한글이 굴림체로 그려지고 고정폭 공백으로 벌어지던 글꼴 문제를 브라우저로 실제 글꼴을 조사해 고침, 세로 여백, 흐름도 스크롤을 화면 높이의 0.63으로 1.4배, 챗 박스의 반복 멘트와 글자 크기와 비율). 포트폴리오에 쓸 재료를 [../PORTFOLIO_NOTES.md](../PORTFOLIO_NOTES.md)에 정리했다. 결정과 근거는 DECISIONS의 2026-11-03 항목 2개에 있다.
- **그 전(2026-11-02):** OpenAI 호환 입구, 이름 변경(Curtain), 사이트 디자인(손그림, 커튼 효과, 흐름도), 내 키로 쓰기와 Render 배포, 시연 예시의 모범 답, 커튼 기록, 폴더 정리(HR 시절 자료를 `docs/archive/`로). 자세한 내용은 DECISIONS의 2026-11-02 항목들과 [worklog.md](worklog.md).
- **알려진 한계:** 규칙 기반이라 우회에 약하다(정규화 전 변형 96문장 중 26%, 정규화 후 알려진 유형 79.8%, 처음 보는 변형 48.3% 탐지. 줄바꿈 쪼개기와 인코딩은 거의 못 막는다). 사용자 인증이 없다. 공개 서버에서는 원문과 방문자 키가 서버를 한 번 거친다(저장하지 않음, [open-risks.md](open-risks.md) R13). 모델이 가명을 답에 쓰지 않으면 복원할 것이 없다.

## 3. 다음 작업과 결정 대기

| 번호 | 항목 | 비고 |
|---|---|---|
| 1 | 저장소 공개 여부 | **결정함(2026-11-03): 공개 유지.** 평가자가 코드와 기록을 봐야 하기 때문이다. 커밋 6개에 개인 이메일이 작성자로 남아 있으나 그대로 둔다 |
| 2 | 커튼 기록의 정확도 보완 | 서버가 가명과 위치를 직접 알려 주면 화면의 짝 추측이 필요 없다(DECISIONS 2026-11-02 커튼 기록 단점). 선택 |
| 3 | 개선 후보 | 가린 대화에 가명 안내문 넣기(모델이 가명을 쓰는 비율), 사용자 인증, 회사 안 설치 안내 |
| 4 | 코드 읽기 | 사용자가 보류했다. 가이드와 진행 기록은 2026-11-03 저장소에서 제거해 내 컴퓨터의 백업 폴더(`Curtain_백업_2026-11-03`)에 있다 |
| 5 | 저장소 `LICENSE` | 2026-11-03 두지 않기로 했다(열람용 공개). `constitution.md`는 HR 시절 문서라 `docs/archive/`로 옮겼다 |
| 6 | 화면 사진 | **완료(2026-11-03).** 화면을 또 바꾸면 `node tools/shoot-screens.mjs <출력폴더>`로 공개 사이트에서 다시 찍는다(Chrome 필요). 결과를 `docs/images/`에 복사한다 |
| 6-1 | GitHub 웹 설정 | 저장소 About 설명과 Website, Topics, 프로필 고정(Pin), 프로필 저장소 `HTH-0/HTH-0`의 README는 사용자가 웹에서 한다(`gh`가 없다). 초안은 2026-11-03 대화에서 만들었다 |
| 7 | 공개 사이트에서 챗 박스 점검 다시 돌리기 | Render 배포는 푸시 약 60초 뒤 새 버전(`conv-note`, 글꼴 `?v=7`)이 된 것을 확인했다. 그 뒤 공개 사이트에서 돌리던 챗 박스 30개 점검은 사용자가 중단해 끝내지 못했다. 내 PC 서버에서는 30/30, 흐름도 단계 21/21 통과했다 |
| 8 | `PORTFOLIO_NOTES.md`, `plan.md` | 2026-11-03 `.gitignore`에 넣었다(개인 메모, 공개 저장소에 올리지 않는다). 백업은 내 컴퓨터의 `Curtain_백업_2026-11-03`에도 있다 |
| 9 | 점검 스크립트 보관 여부 | 사진 촬영 스크립트는 `tools/shoot-screens.mjs`에 보관했다. 챗 박스 30개 점검, 흐름도 단계 점검, 글꼴 조사, 가짜 Gemini 서버, 커튼 기록 점검은 세션 임시 폴더에만 있어 세션이 끝나면 사라진다. 계속 쓰려면 `tools/`로 옮긴다. 점검 방식은 worklog 2026-11-03 항목에 적었다 |
| 10 | 판정 개선(Z 3차) | 사용자가 Z의 사람 판정(45쌍)이 제대로 된 답변이 아니었다고 해서 재판정을 제안했다. 제안 계획: 루브릭을 문서로 고정, 쌍대 비교로 20~25쌍 판정, 원문이 필요한 요청 분류. **사용자 승인 전**(정규화를 먼저 하기로 해서 미룸) |
| 11 | 푸시와 `main` 반영 | 정규화 커밋을 `dev`에 푸시하고, 공개 사이트에 반영하려면 `bash tools/publish-main.sh --dry-run`으로 목록을 보고 사용자 승인 후 `main`에 올린다. 반영하면 사이트가 다시 배포된다 |
| 12 | 우회 후속 개선 | 보고서 `report_bypass_v2.md`의 후속 후보(구분자, `O`와 `l`, 인코딩 해독, 은행 표시 뒤 번호의 오탐 조건). 효과는 새 블라인드 세트(v4)로 잰다 |

## 4. 새 컴퓨터에서 시작하기 (Windows 기준)

필요한 것: Git, JDK 25 LTS(Temurin), Maven 3.9.x, Python 3, PowerShell

```powershell
git clone https://github.com/HTH-0/Curtain.git
cd Curtain
git fetch origin --prune; git branch -r; git switch dev; git pull

# 이 저장소에만 적용하는 커밋 정보
git config user.name "HTH-0"
git config user.email "192852807+HTH-0@users.noreply.github.com"

# 시험 (키 없이 통과한다)
cd backend
mvn -B -q test
cd scripts
python -m unittest test_fake_values test_measure test_build_eval_set test_build_bypass_set test_z_measure test_build_blind_set test_freeze_v2
```

**실행 방법 (`backend` 폴더에서)**

| 하고 싶은 것 | 명령 | 비고 |
|---|---|---|
| 키 없이 시연 | `mvn spring-boot:run "-Dspring-boot.run.arguments=--gateway.demo-answer=true"` | 공개 사이트와 같은 모드. 방문자 키 입력도 동작한다 |
| 서버 키로 실제 Gemini | `mvn spring-boot:run` (환경변수 `GEMINI_API_KEY` 필요) | 키를 등록하기 전에 연 터미널은 `$env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable("GEMINI_API_KEY","User")`로 먼저 불러온다. 모델은 `--gemini.model=…` 인자로 바꾼다(`application.yml`은 동결 해시 때문에 고치지 않는다) |
| 측정 모드 | `--gateway.measurement-mode=true` | 같은 컴퓨터의 요청에만 탐지 결과를 돌려준다. 측정 재현은 [eval/README.md](eval/README.md) |

- 브라우저로 <http://localhost:8080>을 연다. 화면 파일을 고쳤으면 서버를 다시 시작하고 `Ctrl+F5`로 새로 고친다.
- **Chatbox 연결:** API 호스트 `http://localhost:8080`(공개 사이트면 그 주소), API 키는 내 Gemini 키(아무 값이면 서버 기본 응답), 모델 `curtain`. 모델 설정에서 Vision과 도구 사용을 끈다.
- **배포:** `main`을 푸시하면 Render가 저장소의 `Dockerfile`로 다시 빌드해 2분 안팎에 바뀐다. `dev` 푸시는 배포에 영향이 없다. Render에는 환경변수를 넣지 않는다(키 금지). `main`은 `dev`에서 `bash tools/publish-main.sh --dry-run`으로 목록을 확인한 뒤 `-m "메시지"`로 실행해 만들고, 푸시는 따로 한다. 새 파일이 `main`에도 필요하면 `tools/main-whitelist.txt`에 경로를 더한다. 검증용 임시 브랜치는 `--branch 이름`으로 만든다.
- **화면 글꼴:** 제목 글꼴은 화면에 쓰는 글자만 남긴 부분 집합(`static/fonts/Jua-subset.woff2`)이다. 제목, 버튼, 안내 문구에 새 글자를 넣으면 부분 집합을 다시 만들고 `index.html`의 `?v=` 숫자를 올린다(THIRD_PARTY_NOTICES 5-0-1).
- **Claude Code로 작업:** `CLAUDE.md`와 `AGENTS.md`가 적용된다(한국어 응답, 계획 후 승인, 작업 스텝 보고). 첫 지시는 "docs/handoff.md를 읽고 현재 상태와 다음 작업을 알려 줘"면 된다.

## 5. 지켜야 할 규칙

1. 범위나 무대를 바꾸는 결정은 [problem.md](problem.md)와 맞는지 확인하고 [DECISIONS.md](../DECISIONS.md)에 후보, 선택, 이유를 기록한다.
2. 운영 경로는 외부 판정 서비스를 부르지 않는다(NFR-4).
3. 로그와 응답에 원문, 탐지한 값, 가명 대응표, API 키(서버 키와 방문자 키 모두)를 남기지 않는다.
4. 평가 데이터와 시연에는 실제 개인정보를 쓰지 않는다. 가짜 값은 `fake_values.py`로 만든다.
5. 평가 세트, 대량 기준, 탐지 규칙은 측정 전에 해시로 동결하고 측정 결과를 보고 바꾸지 않는다(`docs/eval/set/freeze.json`).
6. API 키를 파일, 로그, 대화, Render 설정에 넣지 않는다. 올리기 전에 추가된 줄에 키 형식이 없는지 검사한다.
7. 외부 LLM이 낸 결과는 검증한 뒤 채택한다(Z는 사람 판정과 85% 이상 일치가 기준이었고 Jev는 채택하지 않았다).
8. 화면은 외부 주소에서 아무것도 불러오지 않는다(글꼴과 그림은 저장소에 직접, AC-8.8). 실존 캐릭터, 로고, 사진을 쓰지 않는다(AC-8.9).
9. 세션을 시작하면 `git fetch origin`으로 원격 브랜치를 먼저 확인하고 `dev`에서 작업한다(`CLAUDE.md` 0번). `main`을 직접 고치거나 `dev`와 `git merge`하지 않는다.

## 6. 문서 지도

| 문서 | 질문 |
|---|---|
| [problem.md](problem.md) | 왜, 누구를 위해 만드는가 |
| [policy.md](policy.md) | 무엇으로부터 무엇을 지키고 어떻게 처리하는가(위협 모델, 데이터 정책) |
| [spec.md](spec.md) | 무엇을 만드는가(기능 요구 FR-1~FR-12와 수용 기준) |
| [design.md](design.md) | 어떻게 만드는가(구성, 흐름, 오류 처리) |
| [dummy-company.md](dummy-company.md) | 가상 회사 HTH와 가짜 데이터 형식 |
| [tasks.md](tasks.md) | 계획과 진행 상태 |
| [open-risks.md](open-risks.md) | 남은 위험 |
| [../DECISIONS.md](../DECISIONS.md) | 왜 그렇게 정했는가(누적) |
| [worklog.md](worklog.md) | 세션별 작업 기록 |
| [eval/](eval/README.md) | 평가 자료(업무 요청 틀, 동결한 평가 세트, 측정 결과)와 측정 재현 방법 |
| [archive/](archive/README.md) | 주제를 바꾸기 전 자료(보관) |
| [../THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) | 외부 자료, 글꼴, 그림의 출처와 라이선스 |
