# 설계서 v2

[명세서 v2](spec.md)를 어떻게 만들지 정한다. 기존 패키지 `com.promptgateway`를 유지하고, 탐지와 가림은 새 패키지 `detect`, `mask`에 둔다. 주제 재정의 이전 설계는 [archive/design-v1-hr.md](archive/design-v1-hr.md)에 있다.

상태: 확정 (2026-10-29, 일주일 일정의 하루 단위 승인에 포함). 구현 중 어긋나면 이 문서를 먼저 고치고 DECISIONS.md에 기록한다.

## 1. 구성요소

| 구분 | 구성요소 | 책임 | 명세 |
|---|---|---|---|
| 유지 | `gateway.InputValidator` | 빈 입력, 최대 길이(4,000자) | FR-1 |
| 신규 | `detect.Finding` | 탐지 하나: 범주(D1~D3), 항목(예: `PHONE`), 시작과 끝 위치, 값. 값은 메모리에만 있다 | FR-2 |
| 신규 | `detect.Detector` (인터페이스) | `List<Finding> detect(String text)`. 범주별 구현체: `PersonalDataDetector`(D1), `SecretDetector`(D2), `DictionaryDetector`(D3) | FR-2 |
| 신규 | `detect.DetectionEngine` | 모든 탐지기를 돌리고, 허용 목록을 빼고, 겹친 탐지를 정리한다 | FR-2 |
| 신규 | `detect.ActionDecider` | 탐지 결과로 허용, 가림, 경고, 차단을 정한다(순수 함수) | FR-3 |
| 신규 | `mask.Masker` | 가명으로 바꾸고 대응표를 만든다. 응답을 복원한다 | FR-4 |
| 변경 | `gateway.ChatGateway` | 검증 → 탐지 → 결정 → (가림) → 외부 AI → 복원 → 로그 순서를 부른다 | 1장 흐름 |
| 유지 | `service.AnswerGenerator`, `GeminiService` | 외부 AI 호출. 인터페이스 하나로 바꿔 끼운다 | FR-5 |
| 변경 | `log.GatewayLogger` | 범주별 개수, 처리, 길이와 해시, 지연. 원문과 값은 남기지 않는다 | FR-7 |
| 변경 | `controller.ChatController` | 처리별 응답과 상태 코드, 측정 모드(같은 컴퓨터 제한 유지) | FR-6, FR-9 |
| 신규 | `static/index.html` | 포털 화면 한 장. 위에서부터 소개 구간(히어로, 문제와 처리 네 가지, 측정 결과), 채팅 구간 순서이다. 이미지는 `static/img/`의 SVG 파일로 분리한다(임시 이미지, 교체 가능) | FR-8 |
| 삭제(3일차) | `judge.*`, `filter.*` (Jev 호출, 패턴 필터, 질문 세트) | 운영 경로는 외부 판정을 부르지 않는다(NFR-4). Z 채점의 Jev 호출은 측정 경로의 Python 도구가 맡는다. 옛 코드는 git 기록에 남아 있다 | NFR-4 |

## 2. 탐지 규칙 (policy.md 3장의 구현)

| 범주 | 항목 | 규칙 | 오탐을 줄이는 검증 |
|---|---|---|---|
| D1 | `RRN` 주민등록번호 | `\d{6}-?\d{7}`, 앞뒤가 숫자가 아님 | 생년월일이 실제 날짜, 뒷자리 첫 숫자 1~4. **검증 숫자는 요구하지 않는다**(2020년 10월 이후 발급 번호는 검증 숫자 체계가 없다) |
| D1 | `FRN` 외국인등록번호 | 같은 형식 | 생년월일, 뒷자리 첫 숫자 5~8 |
| D1 | `PHONE` 휴대전화 | `01[016789]`, 3~4자리, 4자리. 하이픈, 점, 공백, 붙여 쓰기 | 가운데와 끝이 모두 같은 숫자면 예시 값으로 보고 제외(`010-0000-0000`) |
| D1 | `EMAIL` | 일반 이메일 형식 | 허용 목록, `example.com` 같은 예시 도메인 제외 |
| D1 | `ACCOUNT` 계좌번호 | 은행명이나 "계좌" 뒤 20자 안의 `\d{2,6}(-\d{2,6}){1,3}`, 숫자 10~14자리 | 앞의 문맥 단어가 있어야 한다 |
| D1 | `CARD` 카드번호 | 13~19자리(하이픈과 공백 허용), 첫 숫자 3~6 | Luhn 검사 |
| D2 | `API_KEY` | 알려진 접두어 형식: `AKIA`+16, `AIza`+35, `sk-`+20 이상, `ghp_`+36, `xox[abprs]-` | 자리 표시자(`xxxx`, `****`, `your_`, `<...>`) 제외 |
| D2 | `PRIVATE_KEY` | `-----BEGIN ... PRIVATE KEY-----` | 없음 |
| D2 | `PASSWORD` | `password`, `passwd`, `pwd`, `비밀번호`, `비번` 뒤 `:` 또는 `=` 뒤의 값 4자 이상 | 자리 표시자 제외, 서로 다른 글자 3개 이상 |
| D2 | `CONN_STRING` | `scheme://사용자:비밀번호@호스트` | 자리 표시자 제외 |
| D2 | `JWT` | `eyJ`로 시작하는 점 두 개의 세 덩어리 | 각 덩어리 길이 |
| D3 | `CODENAME`, `CLIENT` | 회사 사전의 단어(대소문자 무시) | 없음 |
| D3 | `CLASSIFIED` | 등급 표시 단어 | 없음 |

**겹친 탐지 정리:** 위치가 겹치면 더 긴 탐지를 남긴다. 길이가 같으면 D2 > D3 > D1, 그 안에서는 표의 순서를 따른다. 예를 들어 카드번호 안의 숫자를 계좌로 다시 세지 않는다.

**허용 목록:** 회사 대표 전화, 대표 이메일, 공개 주소. 설정 파일에 둔다.

## 3. 처리 결정 (`ActionDecider`)

```
D2가 하나라도 있음, 또는 D3 CLASSIFIED가 있음 → BLOCK
D1 값이 20개 이상                         → WARN (확인하면 MASK로 보냄)
D1 또는 D3(코드명, 고객사)이 있음          → MASK
아무것도 없음                             → ALLOW
```

대량 기준 20은 설정 값(`detection.bulk-threshold`)이고 측정 전에 동결한다.

## 4. 가림과 복원 (`Masker`)

- 가명 형식: `[항목한글명+순번]`. 예: `[전화1]`, `[이메일2]`, `[주민번호1]`, `[계좌1]`, `[카드1]`, `[코드명1]`, `[고객사1]`. 외부 AI가 무엇이었는지 짐작해 답할 수 있도록 항목을 드러낸다.
- 같은 값은 같은 가명이다. 값 비교는 공백과 하이픈을 지운 형태로 한다(`010-1234-5678`과 `01012345678`은 같은 값).
- 원문에 이미 같은 모양의 글자가 있으면 그 순번을 건너뛴다(AC-4.5).
- 대응표는 `MaskResult` 객체 하나에만 있고 요청이 끝나면 버린다. 로그와 응답 본문(측정 모드 제외)에 싣지 않는다.
- 복원: 응답에서 대응표의 가명을 찾아 원래 값으로 바꾼다. 외부 AI가 가명을 조금 바꿔 쓴 경우(`[전화 1]`, `［전화1］`)도 정규화해서 찾는다. 찾지 못한 가명 수를 센다(복원 성공률의 근거).

## 5. API

`POST /api/chat` 본문 `{"prompt": "...", "confirmed": false}`

| 처리 | 상태 | 본문 |
|---|---|---|
| ALLOW | 200 | `{"action":"ALLOW","answer":"...","findings":[]}` |
| MASK | 200 | `{"action":"MASK","answer":"(복원된 답)","findings":[{"category":"D1","item":"PHONE","count":2}],"sentPrompt":"(외부 AI로 보낸 가린 문장)"}` |
| WARN (미확인) | 200 | `{"action":"WARN","answer":null,"findings":[...],"confirmRequired":true}` |
| BLOCK | 403 | `{"action":"BLOCK","answer":null,"findings":[...],"message":"자격증명이 들어 있어 보내지 않았습니다. 값을 지우고 다시 보내세요."}` |
| 측정 모드 | 200 | 위 항목에 더해 `maskedPrompt`, 항목별 위치, 단계별 지연. 외부 AI는 부르지 않는다 |

- 응답의 `findings`는 범주, 항목, 개수만 싣는다. 값과 위치는 싣지 않는다(측정 모드 제외).
- 입력 검증 실패는 400, 외부 AI 오류는 기존 오류 형식(내부 정보 없음)이다.

- 시연용 응답 모델: `gateway.demo-answer=true`이면 Gemini 대신 받은 문장을 그대로 되돌려 주는 가짜 응답 모델을 쓴다(키 없이 포털을 시연한다). 기본은 false이다.

## 6. 설정

```yaml
detection:
  bulk-threshold: 20
  company-file: company/dictionary.yml   # 코드명, 고객사, 등급 표시, 허용 목록
gateway:
  max-prompt-length: 4000
  measurement-mode: false
```

회사 사전의 내용은 [dummy-company.md](dummy-company.md)에 정의한다.

## 7. 측정 경로 (운영 경로와 분리)

- `backend/scripts/`의 Python 도구가 맡는다. 운영 코드(`backend/src/main`)는 Jev를 부르지 않는다.
- 평가 세트 생성: 업무 요청 틀(생성기 출력)과 가짜 값 생성기(`fake_values.py`)를 합치고, 넣은 값의 범주와 위치를 정답으로 저장한다.
- X, Y, T: 측정 모드 서버에 평가 세트를 보내고 정답과 비교한다(`measure.py`, 옛 HR용 `run_batch.py`는 `backend/scripts/archive_hr/`에 보관).
- Z: 가림 대상 문장마다 Gemini에 원문(가짜 데이터)으로 답 A를, 게이트웨이 경유로 답 B를 받고, 복원 성공률은 코드로, 쓸모는 Jev로 채점한다. Jev 채점은 사람 40~50쌍 판정과 85% 이상 일치할 때 채택한다.

## 8. 테스트 전략

| 수준 | 대상 | 방법 |
|---|---|---|
| 단위 | 탐지기 각각, `DetectionEngine`, `ActionDecider`, `Masker` | 가짜 값으로 입력과 출력만 비교. 경계(검증 실패 값, 자리 표시자, 겹침) 포함 |
| 조립 | `ChatGateway` | 가짜 외부 AI로 처리별 흐름, 외부 호출 횟수, 복원 결과 |
| 웹 | `ChatController` | 상태 코드, 본문 형식, 측정 모드, 값이 응답과 로그에 없음 |
| 측정 도구 | Python | 가짜 서버, 가짜 값 생성기의 검증 규칙 통과 여부 |
| 대화 입구 | `Masker` 대화 가림, `ConversationGateway`, `CompatController` | 가짜 응답 모델로 대화 흐름과 외부 호출 횟수, 같은 대화를 한 턴씩 늘려 보냈을 때 가명이 같은지, OpenAI 응답과 오류 형식, SSE, 원래 값이 밖으로 나가지 않는지 |

## 9. OpenAI 호환 입구 (FR-11, 2026-11-02)

[명세](spec.md) FR-11을 어떻게 만들지 정한다. 기존 `/api/chat` 경로의 코드와 동작은 바꾸지 않는다. 탐지 규칙, 회사 사전, `application.yml`도 고치지 않는다(`freeze.json`의 해시, DECISIONS O6).

### 9.1 구성요소

| 구분 | 구성요소 | 책임 |
|---|---|---|
| 신규 | `compat.CompatController` | `GET /v1/models`, `POST /v1/chat/completions`. 요청을 `ConversationGateway`에 맡기고 OpenAI 형식(일반 JSON 또는 SSE)으로 바꾼다 |
| 신규 | `compat` 패키지의 요청, 응답, 오류 값 클래스 | OpenAI 형식(`ChatCompletionRequest`, `ChatCompletionResponse`, `ModelList`, `OpenAiError`). 값과 위치는 싣지 않는다 |
| 신규 | `compat.CompatExceptionHandler` | `CompatController`에만 적용하는 오류 처리(`@RestControllerAdvice(assignableTypes)`). 기존 `GlobalExceptionHandler`의 `ApiErrorResponse` 형식을 건드리지 않고 OpenAI 오류 형식으로 바꾼다. 외부 AI 오류는 상태 코드 규칙을 기존과 같게 둔다(AC-11.10) |
| 신규 | `gateway.ConversationGateway` | 대화 검증, 모든 메시지 탐지, 가장 강한 처리 결정, 대화 가림, 외부 AI 호출, 복원, 로그 한 줄. 단계 순서만 정하고 판단은 기존 부품이 한다 |
| 확장 | `mask.Masker` | 메시지 여러 개를 한 대응표로 가리는 메서드 추가. **기존 `mask`, `restore`는 바꾸지 않는다** |
| 확장 | `service.AnswerGenerator` | 대화를 받는 메서드 추가. 기본 구현은 대화를 한 문자열로 이어 붙여 기존 메서드를 부르고(가짜 응답 모델용), `GeminiService`는 Gemini의 여러 턴 형식으로 재정의한다. 시연용은 마지막 사용자 메시지를 되돌린다 |
| 확장 | `config.GatewayProperties` | `maxConversationLength`(코드 기본값 20000). `application.yml`은 고치지 않는다 |
| 재사용 | `DetectionEngine`, `ActionDecider`, `GatewayLogger` | 그대로 |

### 9.2 처리 순서

```
POST /v1/chat/completions {model, messages[], stream?}
  1. 검증        messages 비어 있음, 허용 밖 역할, 문자열 아닌 content, 공백뿐, 합계 20,000자 초과 → 400
  2. 탐지        메시지마다 DetectionEngine (역할 무관)
  3. 결정        메시지별 처리 중 가장 강한 것. 차단 > 경고 > 가림 > 허용. 경고는 확인 없이 가림으로 보낸다
  4. 차단        → 외부 AI 호출 없음, 403 OpenAI 오류 (범주별 개수와 안내만)
     허용        → 대화 그대로 외부 AI 호출
     가림/경고    → 모든 메시지를 한 대응표로 가림 → 외부 AI 호출
  5. 복원        답의 가명을 원래 값으로. 가림/경고이면 맨 앞에 안내 한 줄
  6. 응답        stream=false: 일반 JSON, stream=true: 완성된 답을 SSE 한 건 + [DONE]
  7. 로그        한 줄. 길이와 해시는 메시지를 이어 붙인 전체 기준
```

### 9.3 대화 가림 (`Masker`)

- 입력은 메시지별 텍스트와 탐지 결과이다. 메시지 순서, 메시지 안에서는 위치 순서로 처음 보는 값에 번호를 준다. 같은 값(공백, 하이픈, 점, 대소문자 무시)은 같은 가명이다.
- 클라이언트는 매 턴 앞부분 메시지를 그대로 다시 보내므로(이전 답에는 복원된 원래 값이 있다), 앞부분의 번호는 턴이 늘어도 바뀌지 않는다(AC-11.5).
- 원문의 어느 메시지에든 `[전화1]` 같은 글자가 이미 있으면 그 번호를 건너뛴다(AC-4.5를 대화 전체로 확장).
- 대응표는 요청 하나 안에서만 쓰고 버린다.

### 9.4 오류 형식

| 상황 | 상태 | `error.type` | `error.code` |
|---|---|---|---|
| 입력 오류, 읽을 수 없는 본문 | 400 | `invalid_request_error` | `invalid_request` |
| 차단 | 403 | `deskguard_blocked` | `blocked` |
| 외부 AI 인증 실패 | 401 | `upstream_error` | `upstream_unauthorized` |
| 외부 AI 한도 초과 | 429 | `upstream_error` | `upstream_rate_limited` |
| 외부 AI 시간 초과 | 504 | `upstream_error` | `upstream_timeout` |
| 그 밖의 오류 | 500 | `upstream_error` | `internal_error` |

`error.message`는 고정 문구이다. 내부 주소, 키, 외부 응답 본문은 싣지 않는다.

### 9.5 한계

- 진짜 스트리밍이 아니다. 답이 모두 만들어진 뒤에 한 번에 도착한다.
- 인증이 없다. 로컬 시연 전용이다.
- 매 턴 대화 전체를 다시 검사하고 가리므로 대화가 길수록 비용이 늘어난다(합계 20,000자 상한).
- 파일, 이미지, 함수 호출(tools)은 지원하지 않는다.
