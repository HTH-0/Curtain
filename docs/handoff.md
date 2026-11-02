# 이어서 작업하기

다른 컴퓨터나 새 세션에서 이어서 작업할 때 가장 먼저 읽는 문서이다. 세션을 마칠 때마다 갱신한다. 그동안 무엇을 왜 했는지, 지금 코드가 어떻게 생겼는지, 이어서 할 때 조심할 점은 [worklog.md](worklog.md)에 있다. 주제 재정의 이전(HR 헬프데스크 챗봇)의 인수인계 문서는 [archive/handoff-v1-hr.md](archive/handoff-v1-hr.md)에 보관했다.

기준일: 2026-11-02 (Z 1차와 2차(가명 안내문) 측정과 보고서 완료. OpenAI 호환 입구(FR-11)와 Chatbox 연결 시연 완료, 마감 11-06. 코드 읽기는 보류 중).

## 1. 한눈에 보기

- **프로젝트:** Curtain(이전 이름 Deskguard, 2026-11-02 변경. 저장소 이름은 그대로). 회사 직원이 ChatGPT 같은 외부 AI에 보내는 프롬프트를 나가기 전에 검사하는 정보 유출 방지 게이트웨이와 사내 AI 포털이다. 왜 만드는지는 [problem.md](problem.md)(최상위 문서)에 있다.
- **처리:** 개인정보(D1), 자격증명(D2), 회사 기밀(D3)을 게이트웨이 안의 코드로 탐지하고, 허용, 가림(가명으로 바꿔 보내고 답에서 되돌림), 경고(확인 후 가려서 보냄), 차단으로 처리한다. 운영 경로는 외부 판정 서비스를 부르지 않는다.
- **브랜치:** `main` 하나만 쓴다(2026-10-30 정리). 클라우드 작업을 쓰지 않으므로 작업 브랜치를 따로 두지 않고 `main`에서 바로 작업하고 커밋한다. 이전 작업 브랜치(`inspiring-shannon`, `upbeat-cori`, `compassionate-pascal`)는 `main`에 합친 뒤 원격과 로컬에서 모두 삭제했다. 삭제 직전 해시는 `inspiring-shannon` 04c41fc(원격은 0b68383), `upbeat-cori` 9df245d이다.
- **개발 방식:** SDD와 TDD를 병행한다. 기획은 SDD(문제 정의 → 정책 → 명세 → 설계 → 작업 목록), 구현은 TDD(수용 기준 → 실패하는 시험 → 코드)이다. 각 작업의 첫 스텝은 명세 점검이고, 컴파일 오류는 빨강으로 치지 않는다.
- **기술:** Spring Boot 4.1.1, Java 25 LTS, Maven, Python 3(측정 도구). 외부 AI는 Gemini(`gemini-3.6-flash`). Jev는 측정 경로에서 Z 채점에만 쓴다(5~6일차).

## 2. 진행 상태 (일주일 계획, [tasks.md](tasks.md) 맨 위 표)

| 일차 | 작업 | 상태 |
|---|---|---|
| 1 | 문제 정의서, 위협 모델과 데이터 정책, 명세 v2, HR 문서 보관 | 완료 |
| 2 | 설계 v2, 더미 회사(HTH), D1·D2·D3 탐지기, 가짜 값 생성기 | 완료 |
| 3 | 처리 결정, 가림과 복원, 게이트웨이 재조립, 감사 로그, 운영 경로에서 Jev 제거 | 완료 |
| 4 | 포털 화면, 시연용 응답 모델, 가림 응답에 보낸 문장(AC-6.4) | 완료 |
| 5 | 평가 세트 생성과 동결, X·Y·T 측정, Z 준비 | 완료 (평가 세트 596문장, X·Y·T 1차 측정, Z 도구와 시험. [결과](eval/results/report_xyt_v1.md)) |
| 6 | Z 측정(Jev 검증 후), 우회 소량 시험, README와 결과 | **Z 1차 측정 완료(2026-11-01, [결과](eval/results/report_z_v1.md), Jev 불채택·사람 판정 주 결과), Z 2차(가명 안내문)도 완료([결과](eval/results/report_z2_v1.md))**. 아래는 이전 기록: **진행 중** 우회 시험 완료([결과](eval/results/report_bypass_v1.md)), 출처 표기와 README 정리 완료. **Z 실행(사용자 컴퓨터)이 남음**, 그 뒤 Z 보고서 |
| 코드 읽기 | 핵심 코드를 사용자가 읽고 설명할 수 있게 하기 | 1일차 진행 중. [docs/code-reading/](code-reading/)와 [progress.md](code-reading/progress.md) |
| 7 | 여유분, 마무리 정리 | 대기 (`main` 병합은 2026-10-30 끝냄) |

- **시험:** Java 175건과 Python 79건이 통과한다(2026-11-02 확인, 키 없이 통과). Java는 `cd backend`, `mvn -B test`. Python은 `cd backend/scripts`에서 `python -m unittest test_fake_values test_measure test_build_eval_set test_build_bypass_set test_z_measure`이다. 옛 HR용 `run_batch.py`와 `domain_candidates.py`는 시험과 함께 `backend/scripts/archive_hr/`로 옮겼다(보관, 실행하지 않는다).
- **멈춘 지점:** Z 1차, 2차 보고서까지 끝났다. 2026-11-02에 (1) 포털 화면에 소개 구간을 추가했고(`static/index.html`, `static/img/*.svg` 임시 이미지, AC-8.4~8.6), (2) **OpenAI 호환 입구**(`compat/`, FR-11, AC-11.1~11.14, 설계 9절)를 만들고 공식 SDK, 가짜 Gemini, 실제 Gemini, 설치형 Chatbox로 확인했으며, (3) 복원이 대괄호 없는 가명(`전화1`)도 되돌리도록 고쳤다(AC-4.7). 결정과 결과는 DECISIONS 2026-11-02의 여러 항목에 있다. (4) 사이트 소개 구간에 "쓰던 AI 앱 그대로" 안내를 넣었다(AC-8.7, 데스크톱 화면 확인. 좁은 화면은 사용자 확인 대기). (5) 글꼴, 색, 제목 강조를 정리했다(AC-8.8, Pretendard를 `static/fonts/`에 직접 둠, 파랑 포인트 하나). (6) 사이트에서 프로젝트 소개 구간(문제, 처리 네 가지, 측정 결과)을 뺐다. 사이트는 히어로, AI 앱 연결, 포털 세 부분이고, 소개와 측정 결과는 README와 보고서에 둔다(AC-8.4 수정, DECISIONS 2026-11-02). (7) 사이트 분위기를 손그림 크레용 화풍으로 바꿨다(AC-8.8 수정, AC-8.9, Jua 제목 글꼴, 직접 그린 SVG 6개, 밝은 화면만 지원). 화면의 글자를 바꾸면 `static/fonts/Jua-subset.woff2`를 다시 만들어야 한다(THIRD_PARTY_NOTICES 5-0-1). 좁은 화면과 실제 대화 응답의 새 색은 사용자 확인 대기. 참고 이미지 `귀여운.jpg`는 저작권 때문에 `.gitignore`에 넣었다. 이어서 사이트의 이름을 Curtain(ai만 색)으로, 대표 캐릭터를 커튼으로 바꿨다(사이트만, 서버 안내 줄과 모델 이름은 Deskguard 그대로, DECISIONS 2026-11-02). 첫 화면은 화면 전체 높이이고, 들어올 때 커튼이 열리며 스크롤하면 닫힌다(AC-8.10, 외부 라이브러리 없음). 이름을 전부 Curtain으로 바꿨고(모델 `curtain`, 안내 줄 `[Curtain]`), 방문자가 자기 Gemini 키를 넣어 사이트에서 실제 AI를 쓰게 했다(FR-12, 키는 탭에만, 서버는 저장하지 않음, Gemini에는 헤더로). 공개 시연 배포용 `Dockerfile`이 있고 Render 배포는 사용자 가입 후 진행한다. 어두운 화면과 좁은 화면은 사용자 확인 대기. 코드 읽기는 사용자가 보류했다.
- **후보(아직 하지 않음):** 가린 대화에 "`[전화1]`은 가려진 값이니 그대로 쓰라"는 안내문을 넣어 모델이 가명을 쓰는 비율을 높이는 개선(명세와 시험부터), 시연 스크립트, 입력 정규화(우회 탐지 26%).

## 3. 다음 작업

### 내일 할 일 (순서대로)

1. ~~Z 실행~~ 완료(2026-11-01). [report_z_v1.md](eval/results/report_z_v1.md)에 결과와 한계가 있다. 아래 "Z 측정 실행 절차"는 재측정(가명 안내 문구 2차 측정 등)을 할 때만 쓴다.
2. **코드 읽기 이어서**: [code-reading/progress.md](code-reading/progress.md)의 "확인 대기" 항목(`ChatGateway` 질문 4, 5, `ActionDecider` 확인 질문 3개, `InputValidator` 질문 4)을 풀고, 이어서 `PersonalDataDetector`, `SecretDetector`, `DetectionEngine`을 읽는다. 하루 3개가 기준이다. 읽기 가이드는 [code-reading/day1.md](code-reading/day1.md)이고, 2일차 가이드는 아직 없다(요청하면 만든다).
3. ~~Z 보고서~~ 완료. Jev는 일치율 48.9%로 채택하지 않았다(사람 판정이 주 결과).
4. **마무리(작업이 끝난 뒤)**: 폴더 정리, 비공개 전환(4절 4번).


5일차(측정 도구 v2, 업무 요청 틀 3종, 평가 세트 596문장 동결, X·Y·T 1차 측정)는 끝났다. 결과는 [eval/results/report_xyt_v1.md](eval/results/report_xyt_v1.md)이다. 6단계의 Z 도구(`backend/scripts/z_measure.py`, 시험 포함)도 만들어 두었고, **실제 호출은 키가 있는 사용자 컴퓨터에서 한다.**

### Z 2차 측정: 가명 안내문 (완료, 사람 판정은 선택 사항)

**결과는 [report_z2_v1.md](eval/results/report_z2_v1.md)이다. 아래 "남은 것"은 사용자가 사람 판정을 하고 싶을 때만 한다(하지 않기로 했다, DECISIONS 2026-11-02).**

가림 요청 앞에 고정 안내문을 붙이면(AC-4.6, `gateway.mask-notice`) 가림 처리 후 답이 좋아지는지 같은 평가 세트로 다시 쟀다. 결정과 방식은 [DECISIONS.md](../DECISIONS.md) 2026-11-01 "Z 2차 측정 준비"에 있다. 코드는 커밋되어 있고(`89af255`) 안내문 문구는 동결했다.

- **끝난 것:** 2차 수집 263/263(`docs/eval/results/z2/collected.jsonl`, 답 A는 1차 것을 재사용), Jev 채점과 보고(`z2/scores.jsonl`, `z2/report.json`, 답 A 판정은 1차 값으로 고정). 자동 수치(Jev 참고값): 복원 성공 92.0→93.9%, 값 보존 84.5→99.6%, 유지율 56.8→73.7%, 응답 잘림 26→17.
- **남은 것 1, 사람 판정(사용자):** 1차와 같은 45문장 중 고정 seed(20261101)로 뽑은 20문장의 2차 답 B를 판정한다. 판정 화면은 `docs/eval/results/z2/human_judge.html`(브라우저로 열기, 키 없이 동작)이다. 진행은 그 브라우저에만 저장되므로, **다른 컴퓨터에서 이어서 할 때는 처음부터 다시 판정**한다(아직 시작하지 않았다). 판정이 끝나면 화면의 "CSV 저장"으로 `human_sheet_z2_filled.csv`를 내려받아 알려 준다. 기준은 1차와 같다: 받은 직원이 요청한 일을 그대로 쓸 수 있으면 예.
- **남은 것 2, 비교와 보고서(Claude):** 20문장에 대해 1차 사람 판정(`z/human_sheet.csv`와 `z/human_key.json`으로 1차 답 B의 판정을 찾는다)과 2차 사람 판정을 나란히 놓는다. 표본이 작으므로 방향만 보고 구간을 함께 적는다. 이어서 `docs/eval/results/report_z2_v1.md`를 쓰고 README의 Z 칸과 DECISIONS를 갱신한다. 사람 판정을 하지 않기로 하면 Jev 참고값과 값 보존(코드 지표, 믿을 수 있음)만 적고 "사람 판정 미실시"를 한계로 적는다.
- **재현 명령(필요할 때만):** 게이트웨이를 `mvn spring-boot:run "-Dspring-boot.run.arguments=--gemini.model=gemini-3.5-flash-lite --gateway.mask-notice=true"`로 띄우고 `python z_measure.py collect --dir ../../docs/eval/results/z2 --reuse-a ../../docs/eval/results/z/collected.jsonl`을 실행한다.

### Z 측정 실행 절차 (1차, 사용자 컴퓨터, Windows PowerShell)

Z는 평가 세트에서 가림 처리되는 문장 263개에 대해 답 A(원문 직접)와 답 B(게이트웨이 경유, 복원)를 Gemini로 받고, Jev로 채점한다. 입력은 가짜 데이터뿐이다. 기준은 [spec.md](spec.md) AC-10.4a~g, AC-10.5a~b이다.

```powershell
# 0. 최신 작업을 받는다 (새 터미널에서, 키는 setx로 이미 설정되어 있어야 한다)
git fetch origin --prune
git switch main
git pull

# 1. 게이트웨이를 일반 모드로 띄운다 (창 하나, 측정 모드와 시연 모드를 켜지 않는다. GEMINI_API_KEY가 필요하다)
#    모델 인자를 반드시 붙인다. 빠지면 답 B만 application.yml의 모델(3.6 Flash)로 받아 A와 B 비교가 무효가 된다
cd backend
mvn spring-boot:run "-Dspring-boot.run.arguments=--gemini.model=gemini-3.5-flash-lite"

# 2. 다른 창에서 도구를 실행한다
cd backend\scripts

# 2-1. 먼저 5문장만 시험한다 (실제 호출 5회 가량)
python z_measure.py collect --limit 5
python z_measure.py score --limit 5
python z_measure.py report --limit 5

# 2-2. 전체 실행. 한도 오류가 나면 저장된 문장은 건너뛰고 이어서 하므로 같은 명령을 다시 실행하면 된다
python z_measure.py collect
python z_measure.py baseline --count 50
python z_measure.py score
python z_measure.py report

# 3. 사람 판정 표본을 만들고 csv의 usable_1, usable_2 칸에 예 또는 아니오를 채운다
python z_measure.py sample --count 45
# 판정이 끝나기 전에는 human_key.json을 열지 않는다(어느 답이 가림 처리 답인지 알게 된다)
python z_measure.py agree --human ..\..\docs\eval\results\z\human_sheet.csv
```

- 중간 결과와 보고서는 `docs/eval/results/z/`에 쌓인다(`collected.jsonl`, `a2.jsonl`, `scores.jsonl`, `report.json`, `human_sheet.csv`, `human_key.json`). 모두 가짜 데이터이므로 커밋해도 된다. 키는 어디에도 기록되지 않는다.
- 일치율(`agree`의 `all`)이 85% 이상이면 Jev 채점을 채택한다. 미달이면 사람 판정 표본을 주 결과로 쓰고 Jev 수치는 참고로만 적는다(AC-10.5b, 기준은 낮추지 않는다).
- 실행이 끝나면 `docs/eval/results/z/`를 커밋해 푸시하고 알려 주면, 보고서(`report_z_v1.md`)와 DECISIONS 기록은 클라우드에서 작성한다.
- 이 세션에서 도구는 가짜 Gemini와 가짜 Jev 서버로 시험했다. 실제 Jev 응답 형식(`answers.<질문>.noul`)은 T13 스모크 시험과 같은 형식이다.


## 4. 사용자 결정 대기

| 번호 | 항목 | 비고 |
|---|---|---|
| 1 | Z 측정(Gemini와 Jev 호출)을 어디서 실행할지 | **결정함(2026-10-29):** 사용자 컴퓨터에서 실행한다. 절차는 3절 |
| 2 | `constitution.md` 확정 | "초안" 상태. 1장은 problem.md가 대신한다고 적어 두었다 |
| 3 | 확장 후보의 우선순위 | 로컬 모델 문맥 판정(D3 문맥, D4), 브라우저 확장, 파일 업로드. 완성 뒤에 고른다 |
| 4 | 저장소 공개 상태와 R12 | **현재 공개 상태이다**(클라우드 작업 때문에 사용자가 공개로 바꿈). 1차 점검 결과 실제 키와 개인정보는 없다([open-risks.md](open-risks.md) R12). 사용자 결정(2026-10-29): 작업을 모두 끝낸 뒤 폴더를 정리하고 비공개로 되돌린다. 그때까지 키, 실제 개인정보, 실행 로그를 올리지 않는다 |
| 5 | `main` 병합 시점 | **결정함(2026-10-30):** 작업 브랜치를 `main`에 빨리 감기로 합쳤고(`0b68383`), 이후 `main` 하나만 쓴다 |
| 6 | 이 저장소 자체의 `LICENSE` | 정하지 않았다. 비공개로 전환하므로 보류한다(다시 공개할 때 정한다). 외부 자료의 출처 표기는 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)에 했다 |

## 5. 새 컴퓨터에서 시작하기 (Windows 기준)

필요한 것: Git, JDK 25 LTS(Temurin), Maven 3.9.x, Python 3, PowerShell

```powershell
git clone https://github.com/HTH-0/Deskguard.git
cd Deskguard

# 브랜치는 main 하나만 쓴다. 시작할 때마다 다른 원격 브랜치가 없는지 확인한다.
git fetch origin --prune
git branch -r
git switch main
git pull

# 이 저장소에만 적용하는 커밋 정보
git config user.name "HTH-0"
git config user.email "192852807+HTH-0@users.noreply.github.com"

# 시험 (키 없이 통과한다)
cd backend
mvn -B -q test
cd scripts
python -m unittest test_fake_values test_measure test_build_eval_set test_build_bypass_set test_z_measure
```

**포털 시연 (키 없이):** `backend` 폴더에서 아래를 실행하고 브라우저로 http://localhost:8080 을 연다. 시연용 응답 모델은 외부 AI를 부르지 않고 받은 문장을 되돌려 준다. 예시 버튼으로 가림, 차단, 경고, 허용을 확인한다.

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--gateway.demo-answer=true"
```

**실제 외부 AI로 실행:** 환경변수 `GEMINI_API_KEY`를 설정하고 `mvn spring-boot:run`. 키는 aistudio.google.com에서 발급하고 파일이나 대화에 붙여넣지 않는다. Jev 키(`TYPESAFE_API_KEY`)는 Z 측정 때만 필요하다. 키를 등록하기 전에 연 터미널에서는 `$env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable("GEMINI_API_KEY","User")`로 먼저 불러온다. 모델을 바꾸려면 `mvn spring-boot:run "-Dspring-boot.run.arguments=--gemini.model=gemini-3.5-flash-lite"`처럼 인자로 덮어쓴다(`application.yml`은 동결 해시 때문에 고치지 않는다).

**AI 도구 연결(OpenAI 호환 입구):** 서버를 위처럼 실제 키로 띄우고 Chatbox 등의 주소를 `http://localhost:8080/v1`(Chatbox는 API 호스트 `http://localhost:8080`), 모델을 `curtain`으로 설정한다. API 키는 아무 값이나 쓴다. Chatbox는 모델 설정에서 Vision과 도구 사용을 꺼 둔다. 자세한 방법은 [README](../README.md)의 "기존 AI 도구를 게이트웨이에 연결하기"에 있다.

**측정 모드:** `--gateway.measurement-mode=true`. 외부 AI를 부르지 않고 탐지 결과, 위치, 가린 문장, 지연을 돌려준다. 같은 컴퓨터에서 보낸 요청에만 적용된다.

**클라우드 세션 참고:** 클라우드 컨테이너는 JDK 21이다. Temurin 25를 GitHub 릴리스(`github.com/adoptium/temurin25-binaries`)에서 세션 임시 폴더로 받아 `JAVA_HOME`으로 지정하면 JDK 25로 시험할 수 있다. Claude Code로 작업하면 `CLAUDE.md`와 `AGENTS.md`가 적용된다(한국어 응답, 계획 후 승인, 작업 스텝 보고). 새 세션의 첫 지시는 "docs/handoff.md를 읽고 현재 상태와 다음 작업을 알려 줘"면 된다.

## 6. 지켜야 할 규칙

1. 범위나 무대를 바꾸는 결정은 [problem.md](problem.md)와 맞는지 한 줄로 확인하고 [DECISIONS.md](../DECISIONS.md)에 후보, 선택, 이유를 기록한다.
2. 운영 경로는 외부 판정 서비스를 부르지 않는다(NFR-4). Jev는 측정 경로에서 가짜 데이터로만 쓴다.
3. 로그와 응답에 원문, 탐지한 값, 가명 대응표, API 키를 남기지 않는다(측정 모드의 위치와 가린 문장은 같은 컴퓨터에만).
4. 평가 데이터에는 실제 개인정보를 쓰지 않는다. 가짜 값은 `fake_values.py`로 만든다.
5. 평가 세트, 대량 기준, 탐지 규칙은 측정 전에 해시로 동결하고, 측정 결과를 보고 바꾸지 않는다.
6. API 키를 파일, 로그, 대화에 붙여넣지 않는다.
7. 외부 LLM이 낸 결과(Jev 채점 포함)는 검증한 뒤 채택한다(Z는 사람 판정과 85% 이상 일치).
8. 사용자가 코드를 이해하고 설명할 수 있어야 한다(포트폴리오). 코드 읽기는 하루 3개가 기준이고, 확인 질문에 답하고 바로잡은 파일만 "확인 완료"로 센다. 진행은 [code-reading/progress.md](code-reading/progress.md)에 적는다. 승인은 도장이 아니라 결정이어야 하므로, Claude는 계획을 짧게(요약과 되돌릴 수 있는지) 말하고 사용자는 이해가 안 되면 "설명해 줘"로 멈춘다. 작은 수정(입력 정규화 같은 후속 개선)을 사용자가 직접 구현하는 작업은 Z 보고서 뒤에 시간이 남으면 한다. README에는 AI와 사용자의 역할을 나눠 적는다(최종 정리 때). 노션 정리본은 옛 구조 기준으로 `BlockDecider`, `ChatController` 등이 있고, 새 구조 코드의 정리본은 아직 없다.
9. 세션을 시작하면 `git fetch origin`으로 모든 원격 브랜치를 먼저 확인한다(`CLAUDE.md` 0번).

## 7. 문서 지도

| 문서 | 질문 | 상태 |
|---|---|---|
| [problem.md](problem.md) | 왜, 누구를 위해 만드는가 | 확정 |
| [policy.md](policy.md) | 무엇으로부터 무엇을 지키고 어떻게 처리하는가 (위협 모델, 데이터 정책) | 확정 |
| [spec.md](spec.md) | 무엇을 만드는가 (명세 v2, 수용 기준) | 확정 |
| [design.md](design.md) | 어떻게 만드는가 (설계 v2) | 확정 |
| [dummy-company.md](dummy-company.md) | 가상 회사 HTH와 가짜 데이터 형식 | 확정 |
| [tasks.md](tasks.md) | 일주일 계획과 진행 상태 | 갱신 |
| [constitution.md](constitution.md) | 바뀌지 않는 원칙 | 초안(1장은 problem.md가 대신) |
| [open-risks.md](open-risks.md) | 무엇이 불확실한가 | 재정의 이전 기준, 6일차에 정리 |
| [../DECISIONS.md](../DECISIONS.md) | 왜 그렇게 정했는가 | 누적 |
| [worklog.md](worklog.md) | 무엇을 했고, 코드는 어떻게 생겼고, 무엇을 조심하는가 | 세션마다 추가 |
| [images/portal-demo.png](images/portal-demo.png) | 포털 시연 화면 | 4일차 |
| [archive/](archive/) | 재정의 이전 문서(HR 명세, 설계, 정책, 인수인계) | 보관 |
| [eval/](eval/) | 평가 데이터. `templates/`(생성기 3종의 틀), `set/`(평가 세트 596문장, 우회 세트 120문장, 동결 목록), `results/`(X·Y·T와 우회 보고서). 나머지(후보 302개, 라벨링 시트 등)는 HR 시절 자료로 쓰지 않는다 | 갱신 |
| [code-reading/](code-reading/) | 코드 읽기 가이드와 진행 기록 | 갱신 |
| [../THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) | 외부 자료의 출처와 라이선스 | 확정 |
