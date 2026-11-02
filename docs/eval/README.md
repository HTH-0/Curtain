# 평가 자료

Curtain의 탐지와 가림이 얼마나 잘 동작하는지 재는 자료이다. 기준은 명세 FR-10(AC-10.1~10.7)이고, 결과 요약은 README의 "측정 결과"에 있다. 모든 문장은 **가짜 값**으로 만든 합성 문장이며 실제 개인정보는 없다. 주제를 바꾸기 전(HR 챗봇)의 평가 자료는 [../archive/eval-hr/](../archive/eval-hr/README.md)로 옮겼다.

## 파일

| 위치 | 내용 |
|---|---|
| [template_prompt.md](template_prompt.md) | 업무 요청 틀을 만들 때 생성형 AI 3종에 넣은 지시문 |
| `templates/` | 생성형 AI 3종(ChatGPT, Claude, Gemini)이 만든 업무 요청 틀. 값 자리에 자리 표시자가 있다 |
| `set/eval_v1.jsonl` | 평가 세트 596문장(틀 + 가짜 값, 정답은 넣은 값의 항목과 위치) |
| `set/bypass_v1.jsonl` | 우회 시험 세트 120문장(전각 숫자, 띄어쓰기 등 변형) |
| `set/freeze.json`, `set/freeze_bypass.json`, `*.sha256` | 측정 전에 동결한 해시(평가 세트, 틀, 탐지 설정, 대량 기준). **고치지 않는다** |
| `results/report_xyt_v1.md`, `xyt_v1.json` | X(범주별 탐지율), Y(정상 문장 오탐율), T(지연) |
| `results/report_bypass_v1.md`, `bypass_v1.json` | 우회 시험 |
| `results/report_z_v1.md`, `results/z/` | Z 1차: 가림 후 답의 쓸모(사람 판정 45쌍이 주 결과, Jev 채점은 일치율 48.9%로 채택하지 않음) |
| `results/report_z2_v1.md`, `results/z2/` | Z 2차: 가림 요청에 가명 안내문을 붙였을 때(사람 판정 없이 코드 지표 중심) |

## 측정 재현 (Windows PowerShell, `backend/scripts`에서)

평가 세트와 동결 해시는 이미 저장소에 있다. 다시 만들 필요는 없지만, 같은 seed로 만들면 같은 파일이 나온다.

```powershell
# 평가 세트와 우회 세트 다시 만들기(같은 파일이 나와야 한다)
python build_eval_set.py --templates ../../docs/eval/templates --out ../../docs/eval/set/eval_v1.jsonl
python build_bypass_set.py --out ../../docs/eval/set/bypass_v1.jsonl
```

**X·Y·T와 우회 시험:** 다른 창에서 측정 모드 서버를 띄운 뒤 측정한다. 외부 AI와 키가 필요 없다.

```powershell
# 창 1 (backend 폴더)
mvn spring-boot:run "-Dspring-boot.run.arguments=--gateway.measurement-mode=true"
# 창 2 (backend/scripts 폴더)
python measure.py --input ../../docs/eval/set/eval_v1.jsonl --out result.json
python measure.py --input ../../docs/eval/set/bypass_v1.jsonl --out bypass.json
```

**Z(가림 후 답의 쓸모):** 가림 처리되는 263문장에 대해 답 A(원문을 Gemini에 직접)와 답 B(게이트웨이 경유, 복원)를 받는다. `GEMINI_API_KEY`가 필요하고, Jev 채점까지 하려면 `TYPESAFE_API_KEY`도 필요하다. 입력은 가짜 값뿐이다.

```powershell
# 창 1 (backend 폴더): 일반 모드. 모델 인자를 반드시 붙인다(빠지면 답 B만 다른 모델로 받아 비교가 무효가 된다)
mvn spring-boot:run "-Dspring-boot.run.arguments=--gemini.model=gemini-3.5-flash-lite"
# 창 2 (backend/scripts 폴더): 먼저 5문장으로 시험하고 전체를 돌린다. 중단되면 같은 명령으로 이어서 한다
python z_measure.py collect --limit 5
python z_measure.py collect
python z_measure.py baseline --count 50
python z_measure.py score
python z_measure.py report
# 사람 판정 표본(판정을 마치기 전에는 human_key.json을 열지 않는다)
python z_measure.py sample --count 45
python z_measure.py agree --human ..\..\docs\eval\results\z\human_sheet.csv
```

- Z 2차(가명 안내문)는 서버에 `--gateway.mask-notice=true`를 더하고 `python z_measure.py collect --dir ../../docs/eval/results/z2 --reuse-a ../../docs/eval/results/z/collected.jsonl`로 수집한다.
- 사람 판정 화면은 `results/z/human_judge.html`, `results/z2/human_judge.html`(브라우저로 열기, 키 없이 동작)이다.

## 규칙

- 평가 세트, 틀, 탐지 설정, 대량 기준은 측정 전에 해시로 동결했고 결과를 보고 바꾸지 않았다. 탐지 규칙을 고치면 새 버전(v2)으로 다시 동결하고 다시 잰다.
- 외부 LLM의 채점(Jev)은 사람 판정과 85% 이상 일치할 때만 채택한다.
