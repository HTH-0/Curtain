# HR 시절 평가 자료 (보관)

주제를 바꾸기 전(사내 HR 헬프데스크 챗봇의 입력 가드레일) 도메인 평가 세트를 만들던 자료이다. 지금의 Curtain에서는 쓰지 않는다. 2026-11-02 `docs/eval/`에서 옮겼다.

| 파일 | 내용 |
|---|---|
| `generation_prompt.md` | HR 도메인 질문 생성 지시문 |
| `raw/` | 생성형 AI 3종이 만든 원본 질문 |
| `domain_candidates.csv`, `domain_filter_report.md` | 자동 필터를 거친 후보와 보고 |
| `domain_labeling_sheet.csv`, `labeling_guide.md` | 블라인드 라벨링 시트와 안내(라벨링은 하지 않고 주제를 바꿨다) |
| `koica_hr_questions_raw.csv` | 현실성 세트 원본(아래 출처) |
| `paused_6_2/` | 중단한 분석 도구의 빈 껍데기와 시험 |

## KOICA 질의응답 세트의 출처

- 출처: 한국국제협력단, 「인사규정 질의 응대 서비스 질의응답 세트」(2025-10-31 기준), 공공데이터포털 <https://www.data.go.kr/data/15152718/fileData.do>. 이용허락범위 제한 없음(2026-10-28 확인). 이 파일을 쓰는 글에는 출처를 표시한다.
- 원본 SHA-256: `1d3fedc6cb9bbc2d143227faac8160dc1bf30f70efb0d48ef7175f4af1365abc` (인코딩 UTF-8 BOM). 원본은 고치지 않았다.
- 직원이 실제로 묻는 질문 25개를 골랐다. 정상 문장뿐이라 오탐율 확인용이었다.
