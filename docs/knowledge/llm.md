# LLM 기능 운영 — 프롬프트·모델·평가

> 출처: **NIST AI RMF GenAI Profile · OWASP GenAI LLM Top 10 2026 · AWS
> Bedrock 문서**(공개 원전) + 팀 내부 전문 에이전트 블루프린트 §11 LLMOps에서
> 이 레포 규모에 맞는 부분을 벤더링 (블루프린트는 로컬
> `_workspace/harness-reference/`, 비커밋). 제품 맥락: Bedrock 기반 이력서
> 요약(실재), 모의 면접 질문 생성(예정).
> **이 문서는 원본 없이 자립한다** — 판단 기준을 본문에 옮겨 담았다. 로컬
> 경로는 작성 이력이지 참조 의무가 아니다.
>
> 에이전트 중립 문서다 — 현재 소비자는 llm-reviewer와 prompt-change 스킬.

## 핵심 규칙: eval 비교 없는 프롬프트 변경 금지

LLM 출력은 비결정적이라 "바꿨더니 좋아 보인다"는 증거가 아니다. 변경 전
baseline을 같은 데이터셋으로 측정하고, 변경 후 같은 셋으로 재측정한 비교가
있어야 머지한다. eval 셋이 없는 기능이라면 셋 구축이 변경보다 선행이다.

## Eval 데이터셋 분류 (10분류 — 필요한 것부터 채운다)

```text
normal / boundary / adversarial / historical-incidents / prompt-injection
/ authorization / tool-failure / stale-context / ambiguous-input / cost-latency
```

- **historical-incidents가 성장 엔진이다**: 운영에서 이상 출력이 관찰될
  때마다 그 입력을 셋에 재투입한다 — 같은 사고는 두 번 통과하지 못한다.
- 이력서 요약이라면 최소: normal(대표 이력서), boundary(극단 길이·빈 섹션),
  prompt-injection(이력서 본문에 지시문 삽입 — 외부 작성 콘텐츠다),
  cost-latency(토큰 상한).

## 평가 3층

1. **결정적 검사** (싸다 — 전부 자동화): 출력 JSON schema, 필수 필드,
   값 범위, secret·PII 패턴, 최대 토큰·비용.
2. **모델 기반 평가**: 결론 정확성, 근거-결론 일치, 놓친 위험. grader
   프롬프트도 버전 관리 대상이다.
3. **인간 평가**: critical false negative 검토, 자동 grader와 사람의
   일치도 교정. 운영 사례의 셋 재투입 판단.

## 변경·릴리스 단위

릴리스 단위는 프롬프트 문자열 하나가 아니라 **모델 ID + 프롬프트 +
파라미터 + 출력 스키마 + eval 셋 버전의 묶음**이다. 어느 하나만 바뀌어도
비교 측정 대상이고, 롤백 대상도 이 묶음이다.

## Prompt injection — 사용자 입력이 프롬프트에 들어갈 때

이력서·자기소개서 등 사용자 업로드 문서는 외부 작성 콘텐츠다. 본문에
"이전 지시를 무시하라"류 지시문이 들어올 수 있다.

- system instruction과 사용자 콘텐츠를 구조적으로 분리한다(콘텐츠는
  data 필드로).
- 콘텐츠 안의 명령을 실행하지 않는 규칙을 프롬프트에 명시한다.
- prompt-injection eval 분류로 방어를 회귀 측정한다.

## Bedrock 사용 시 함정 (AWS 문서 원전)

- Guardrails의 sensitive-information filter는 `tool_use` 인자 안의 PII를
  검사하지 않는다 — tool argument 검증은 별도 구현.
- model invocation log에는 guardrail 적용 전 원본 입력이 남을 수 있다 —
  로그 수집 전 redaction 별도 필요 (이력서는 PII 덩어리다).

## 벤더 중립

eval 셋과 결과 저장 구조를 특정 공급자 API에 종속시키지 않는다
(OpenAI Evals 플랫폼이 2026-11 종료되는 사례). 파일 기반 manifest +
자체 러너면 공급자를 바꿔도 기준이 유지된다.

## 우리가 겪은 것

아직 없다. 사고·리뷰에서 배운 것이 생기면 날짜와 함께 한 줄 추가한다
(형식: README의 "어떻게 늘리는가").
