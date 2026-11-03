# Curtain

**외부 AI에 보내는 문장에서 민감한 값만 커튼 뒤로 가리는 정보 유출 방지 게이트웨이입니다.**

직원이 ChatGPT 같은 외부 생성형 AI에 업무 내용을 붙여 넣으면, 문장이 나가기 전에 검사합니다. 전화번호 같은 개인정보와 회사 기밀은 가명으로 바꿔 보내고, 돌아온 답에서 원래 값으로 되돌립니다. 비밀번호와 API 키는 아예 보내지 않습니다. 가상 회사(HTH)와 가짜 데이터로 만든 개인 프로젝트입니다.

**[공개 시연 열어 보기](https://curtain-h8id.onrender.com/)** (설치가 필요 없습니다. 무료 서버라 첫 접속은 1분쯤 걸릴 수 있습니다.)

![Curtain 첫 화면](./docs/images/hero.png)

## 동작 방식

![나, 닫힌 커튼(가림), AI는 가명만 봄, 걷힌 커튼(복원), 나](./docs/images/flow.png)

| 처리 | 조건 | 동작 |
|---|---|---|
| 허용 | 탐지된 값이 없습니다 | 그대로 보냅니다 |
| 가림 | 개인정보, 회사 코드명, 고객사 이름이 있습니다 | `[전화1]` 같은 가명으로 바꿔 보내고, 돌아온 답에서 원래 값으로 되돌립니다 |
| 경고 | 개인정보가 20개 이상입니다 | 확인을 받은 뒤 가려서 보냅니다 |
| 차단 | 비밀번호, API 키, 보안 등급 표시가 있습니다 | 보내지 않고 이유를 알립니다 |

- 탐지는 정규식, 검증 숫자(카드 번호 등), 회사 사전으로 합니다. 검사를 위해 민감한 문장을 다른 외부 서비스로 보내지 않습니다.
- 로그에는 원문, 탐지한 값, 가명 대응표, API 키를 남기지 않습니다.
- 대화는 서버에 저장하지 않습니다.

## 써 보기

| 방법 | 내용 |
|---|---|
| 바로 체험하기 | 예시 버튼을 누르면 가림, 차단, 경고, 허용을 확인할 수 있습니다. 외부 AI를 부르지 않는 시연 모드이며, 미리 써 둔 답 속 가명은 실제로 복원됩니다 |
| 내 키로 써 보기 | 내 Gemini 키([Google AI Studio](https://aistudio.google.com/apikey)에서 무료 발급)를 넣으면 실제 Gemini의 답을 받습니다 |
| 내 앱에 연결하기 | Chatbox 같은 OpenAI 방식 앱의 API 호스트에 사이트 주소, API 키에 내 Gemini 키, 모델에 `curtain`을 넣으면 앱의 모든 대화가 Curtain을 거칩니다 |

![커튼 기록: 내가 쓴 문장, AI가 받은 문장, 되돌린 값](./docs/images/portal-demo.png)

가림이 일어나면 답마다 **커튼 기록**이 붙어, 내가 쓴 문장과 AI가 받은 문장과 되돌린 값을 비교해 볼 수 있습니다.

- 내 Gemini 키는 브라우저 탭에만 보관하며, 서버는 그 요청에만 쓰고 저장하거나 기록하지 않습니다.
- 공개 시연 서버에는 문장이 한 번 도착합니다. 실제 개인정보는 넣지 마세요. 회사에서 쓸 때는 같은 서버를 사내에 설치해 원문이 밖으로 나가지 않게 하는 것을 전제로 합니다.
- OpenAI 호환 입구는 `POST /v1/chat/completions`와 `GET /v1/models`입니다. 글자만 받으므로 Chatbox에서는 이미지와 도구 사용 설정을 끄세요.

## 측정 결과

596개의 합성 문장(생성형 AI가 만든 업무 요청 틀에 코드가 만든 가짜 값을 채운 문장)으로 측정했습니다. 평가 세트와 탐지 설정은 측정 전에 해시로 고정했고, 결과를 보고 규칙을 바꾸지 않았습니다. 재현 방법은 [평가 자료](./docs/eval/README.md)에 있습니다.

| 지표 | 결과 |
|---|---|
| 개인정보 탐지율 | 98.1% |
| 자격증명 탐지율 | 81.7% |
| 회사 기밀 탐지율 | 100% (사전과 같은 값을 넣은 결과입니다) |
| 정상 문장 오탐 | 0 / 106 |
| 처리 지연 | 서버 중앙값 0ms, 95백분위 1ms 이하 |
| 가려도 답을 쓸 수 있는 비율 | 91.7% (사람이 판정한 45쌍 중, 원문으로 쓸 수 있던 답 36개 가운데 33개) |
| 우회 문장 탐지율 | 26% (변형 96문장 중 25개) |

상세 수치와 신뢰구간은 [탐지 보고서](./docs/eval/results/report_xyt_v1.md), [우회 보고서](./docs/eval/results/report_bypass_v1.md), [가림 후 유용성 보고서](./docs/eval/results/report_z_v1.md)에 있습니다.

**한계**

- 전각 숫자, 한글 숫자, 글자 띄어쓰기 같은 우회에 약합니다. 입력 정규화가 다음 과제입니다.
- 값 모양만으로는 구분할 수 없는 비밀번호와 계좌 번호를 놓칩니다.
- 합성 문장으로 측정해서 실제 직원 문장보다 높게 나왔을 수 있습니다.
- 사용자 인증이 없고, 모델이 답에 가명을 쓰지 않으면 복원할 것이 없습니다. 남은 위험은 [open-risks.md](./docs/open-risks.md)에 정리했습니다.

## 실행하기

JDK 25, Maven 3.9, Python 3이 필요합니다. API 키 없이 시연과 시험이 됩니다.

```bash
# 키 없이 시연 (http://localhost:8080)
cd backend
mvn spring-boot:run "-Dspring-boot.run.arguments=--gateway.demo-answer=true"

# 서버 키로 실제 Gemini (환경변수 GEMINI_API_KEY 필요)
mvn spring-boot:run

# 자동 시험: Java 183건, Python 79건
mvn -B test
cd scripts && python -m unittest test_fake_values test_measure test_build_eval_set test_build_bypass_set test_z_measure
```

공개 사이트는 저장소의 `Dockerfile`로 Render에 배포하며, `main`에 올리면 자동으로 다시 배포됩니다.

## 구조

```
backend/src/main/java/com/promptgateway/
  detect/      탐지와 처리 결정
  mask/        가명 만들기와 복원
  gateway/     처리 순서
  controller/  포털 API
  compat/      OpenAI 호환 입구
  service/     응답 모델(서버 키 Gemini, 방문자 키 Gemini, 시연 응답)
  log/         원문 없는 감사 로그
backend/src/main/resources/static/   포털 화면 한 장(외부 라이브러리 없음)
backend/scripts/                     평가 세트 생성과 측정 도구(Python)
docs/                                문제 정의, 정책, 명세, 설계, 평가 자료
```

## 개발 방식

명세를 먼저 쓰고(SDD) 실패하는 시험을 먼저 만든 뒤 구현했습니다(TDD). 문서 순서는 [문제 정의](./docs/problem.md), [정책](./docs/policy.md), [명세](./docs/spec.md), [설계](./docs/design.md)입니다. 탐지율처럼 확률적인 품질은 시험이 아니라 측정으로 검증했습니다.

코드의 대부분은 Claude Code가 작성했습니다. 저는 주제와 범위, 설계 방향, 검토, 실제 Gemini와 Chatbox와 공개 사이트에서의 동작 확인, 사람 판정을 맡았습니다.

처음에는 사내 HR 챗봇의 프롬프트 주입 방어 게이트웨이를 만들다가 2026-10-29에 지금 주제로 바꿨습니다. 결정의 근거([DECISIONS.md](https://github.com/HTH-0/Curtain/blob/dev/DECISIONS.md))와 전체 작업 기록, 이전 주제의 자료는 [`dev` 브랜치](https://github.com/HTH-0/Curtain/tree/dev)에 있습니다.

## 출처와 라이선스

글꼴(Pretendard, Jua)은 SIL Open Font License 1.1이고, 그림은 직접 그린 SVG이며, 평가 데이터의 값은 코드가 만든 가짜 값입니다. 자세한 내용은 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)에 있습니다. 코드는 열람용으로 공개하며 별도의 라이선스 파일은 없습니다.
