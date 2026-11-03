# 외부 자료의 출처와 라이선스

이 저장소에는 외부 공개 자료가 들어 있다. 주제를 바꾸기 전(HR 챗봇) 자료는 2026-11-02 `docs/archive/` 아래로 옮겨 보관한다. 출처, 라이선스, 가공 내용을 아래에 적는다. 라이선스는 2026-10-29에 각 자료의 공식 페이지에서 확인했다. 이 저장소의 자체 코드와 문서에는 별도의 `LICENSE` 파일을 두지 않고 열람용으로 공개한다(`DECISIONS.md` 참고).

**2026-11-03 갱신:** 아래 1~3절이 가리키는 `docs/archive/dataset.csv`와 `docs/archive/eval-hr/koica_hr_questions_raw.csv`는 이 날 작업 트리에서 제거했다. 두 파일은 과거 커밋과 태그 `before-cleanup`에는 남아 있으므로, 출처와 조건은 그대로 적어 둔다.

## 1. deepset/prompt-injections (`docs/archive/dataset.csv`의 일부 행)

- 출처: deepset, 「prompt-injections」, Hugging Face. <https://huggingface.co/datasets/deepset/prompt-injections>
- 라이선스: Apache License 2.0 (카드 메타데이터 `apache-2.0`). 전문: <https://www.apache.org/licenses/LICENSE-2.0>
- 가공: 사용자가 이 데이터셋에서 골라 가져온 행(공격 문장 일부와 정상 문장 약 98건)을 `docs/archive/dataset.csv`에 합쳤고 `label`, `type`, `split`, `lang` 열을 붙였다. 원본에 섞여 있던 정상 항목 일부는 `normal`로 재분류했다(2026-09-16 기록). 영어와 독일어 문장이다. 행 단위 출처 열이 없어 정확한 행 수는 `DECISIONS.md`의 작성 기록에 의존하고, 문장을 한 글자도 고치지 않았는지는 원본과 대조해 확인하지 않았다.
- 이 저장소의 `docs/archive/dataset.csv`는 원 데이터셋과 다른 파일이며, 원 데이터셋의 보증이나 승인을 받은 것이 아니다.

## 1-1. `docs/archive/dataset.csv`의 직접 작성 행

- `docs/archive/dataset.csv`의 처음 160행(공격 100건과 정상 60건)은 외부 데이터를 복사한 것이 아니라, 공개 데이터셋들에 공통적으로 보고되는 대표 패턴을 참고해 Claude가 직접 작성하거나 각색한 문장이다(`DECISIONS.md` 2026-09-16). 이후 추가한 문장 중 일부는 사용자와 Claude가 작성했다.

## 2. JailbreakBench JBB-Behaviors (`docs/archive/dataset.csv`의 일부 행)

- 출처: JailbreakBench, 「JBB-Behaviors」, Hugging Face. <https://huggingface.co/datasets/JailbreakBench/JBB-Behaviors>
- 라이선스: MIT License. 전문은 아래와 같다(원문: <https://github.com/JailbreakBench/jailbreakbench/blob/main/LICENSE>).
- 가공: 사용자가 이 데이터셋에서 가져온 유해 행동 98건(아동 성착취 관련 2건은 제외)과 각각의 정상 짝 100건을 `source_category`(원본의 세부 범주) 열과 함께 `docs/archive/dataset.csv`에 합쳤다(2026-09-16 기록). 문장을 원본과 대조해 확인하지는 않았다.
- 주의: 일부 내용은 불쾌할 수 있다. 시험용으로만 쓰며 실행 가능한 지침을 담지 않는다.
- 인용 요청: 카드가 벤치마크 논문의 인용을 요청한다. P. Chao, E. Debenedetti, A. Robey, M. Andriushchenko, F. Croce, V. Sehwag, E. Dobriban, N. Flammarion, G. J. Pappas, F. Tramèr, H. Hassani, E. Wong, "JailbreakBench: An Open Robustness Benchmark for Jailbreaking Large Language Models", NeurIPS 2024 Datasets and Benchmarks Track. 카드는 구성 데이터셋(AdvBench: Zou et al., 2023, arXiv:2307.15043, TDC와 HarmBench: Mazeika et al., 2024, arXiv:2402.04249)도 함께 인용하기를 권한다.

```
MIT License

Copyright (c) 2023 JailbreakBench Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 3. KOICA 인사규정 질의응답 세트 (`docs/archive/eval-hr/koica_hr_questions_raw.csv`)

- 출처: 한국국제협력단, 「인사규정 질의 응대 서비스 질의응답 세트」(2025-10-31 기준), 공공데이터포털. <https://www.data.go.kr/data/15152718/fileData.do>
- 이용허락범위: 제한 없음(2026-10-28 확인). 이 파일을 쓰는 글에는 출처를 표시한다.
- 가공: 원본에서 직원이 실제로 묻는 질문 25개를 골랐고 원본은 고치지 않았다(`docs/eval/README.md`). 주제 재정의(2026-10-29) 이후 이 파일은 평가에 쓰지 않는다.

## 4. 생성형 AI가 만든 자료

- `docs/eval/templates/`의 업무 요청 틀은 ChatGPT, Claude, Gemini가 만든 문장이다. 사람이 고르거나 고치지 않았다.
- 코드와 문서의 상당 부분은 Claude Code가 작성했고 사용자가 검토했다. 커밋 메시지에 공동 작성자가 표시되어 있다.
- 평가 세트의 값(이름, 전화, 카드, 키 등)은 `backend/scripts/fake_values.py`가 무작위로 만든 가짜 값이며 실제 사람과 관계없다. 무작위 값이 우연히 실제 번호와 같을 수는 있다.

## 5. 측정에 쓰는 외부 서비스 (자료가 아님)

- 응답 모델: Google Gemini API(무료 티어). 판정 채점: TypeSafe의 Jev(`jev-1.14.0`, Z 측정에서만). 각 서비스의 약관을 따른다. 입력은 가짜 데이터뿐이다(`docs/open-risks.md` R8, R11).

## 5-0. 웹 화면의 글꼴 (Pretendard)

- 출처: Kil Hyung-jin, 「Pretendard」 v1.3.9(npm 패키지 `pretendard`의 `PretendardVariable.woff2`). <https://github.com/orioncactus/pretendard>
- 라이선스: SIL Open Font License 1.1. 전문은 `backend/src/main/resources/static/fonts/Pretendard-LICENSE.txt`에 있다(2026-11-02 원본 저장소에서 받음).
- 가공: 없음. 파일을 고치지 않고 `static/fonts/`에 두어 포털 화면(`static/index.html`)이 같은 주소에서 불러온다. 외부 CDN을 쓰지 않으므로 화면을 열 때 외부로 요청이 나가지 않는다.
- 디자인 참고: 글꼴 선택, 색을 줄이는 방식, 구간 구성의 원칙은 plan-et.co.kr을 참고했으나 그 사이트의 글, 이미지, 로고는 쓰지 않았다(spec AC-8.6).

## 5-0-1. 웹 화면의 제목 글꼴 (Jua)

- 출처: The Jua Project Authors, 「Jua」(주아체, 배달의민족 폰트를 바탕으로 한 Google Fonts 글꼴). <https://github.com/google/fonts/tree/main/ofl/jua>
- 라이선스: SIL Open Font License 1.1. 전문은 `backend/src/main/resources/static/fonts/Jua-OFL.txt`에 있다(2026-11-02 원본 저장소에서 받음).
- 가공: 화면에 쓰는 글자만 남기도록 부분 집합으로 줄였다(`Jua-subset.woff2`, 2026-11-03 화면 정리 때 다시 만들어 479자, 약 94KB, `pyftsubset`). 화면의 글자를 바꿔 새 글자가 생기면 글꼴을 다시 만들어야 하며, 없는 글자는 Pretendard로 대신 보인다. 라이선스 원문의 저작권 줄(`Copyright 2018 The Jua Project Authors`)에는 예약 글꼴 이름(Reserved Font Name) 지정이 없다(2026-11-02 원문 확인). 그래서 부분 집합 파일에도 같은 라이선스 전문을 함께 둔다.

## 5-0-2. 웹 화면의 그림

- `static/img/`의 `curtain.svg`(커튼 캐릭터), `curtain-icon.svg`(로고와 아이콘), `step-1~3.svg`, `me.svg`(흐름도의 나), `curtain-closed.svg`와 `curtain-open.svg`(흐름도의 닫힌 커튼과 걷힌 커튼)는 이 프로젝트를 위해 Claude Code가 직접 그린 SVG이다. 이전의 방패 캐릭터 그림(`mascot.svg`, `masked-phone.svg`, `shield.svg`)은 2026-11-02 커튼 캐릭터로 바꾸면서 지웠다. 굵은 윤곽선과 크레용 질감의 손그림 화풍만 사용자가 준 참고 이미지(`귀여운.jpg`, 저장소에 올리지 않음)에서 가져왔다. 그 이미지 속 실존 캐릭터는 그리지 않았고 쓰지 않았다(spec AC-8.9).

## 5-1. 사용하지 않은 자료

- KorSET(`datumo/KorSET`), CAGE, RICoTA는 접근 승인과 비상업 조건이 있어 사용하지 않았고 저장소에도 없다(`docs/archive/research/domain-sources.md`).
