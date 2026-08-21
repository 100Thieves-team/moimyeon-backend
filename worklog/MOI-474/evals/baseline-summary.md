# 트리거 베이스라인 — requirement-implementation (라우팅 등록 전)

- 날짜: 2026-08-21
- 조건: AGENTS.md 라우팅 미등록 상태의 자발 트리거. 스킬 2개만 존재
  (issue-context, requirement-implementation). 프롬프트 세트:
  `.agents/evals/trigger/requirement-implementation.tsv` (양성 5·음성 5·경계 2)
- 실행: claude 2.1.238 (N=2, 격리 워크트리, max-turns 4, bypassPermissions),
  codex-cli 0.149.0 (N=1, read-only 샌드박스, 메인 트리). 240초 상한.
- 원본: `trigger-claude-20260821-1750.csv`, `trigger-codex-20260821-1750.csv`.
  raw 트랜스크립트(20MB)는 커밋하지 않고 로컬 보존한다(gitignore).
  **CSV의 detected 열은 1차 감지 패턴(경로 grep)의 값이라 오탐이 섞여 있다**
  — 확정치는 `.agents/evals/score.py`(호출 이벤트만 파싱) 재집계 기준.

## 확정 결과 (score.py 재집계)

| | 양성 | 음성 오호출 | 경계 |
| --- | --- | --- | --- |
| claude (N=2) | 10/10 (100%) | 0/10 (0%) | 4/4 호출 |
| codex (N=1) | 5/5 (100%) | 0/5 (0%) | 2/2 호출 |

경계 b1(스펙·연결·구현 전부)·b2(테스트부터)는 양 런타임 모두 호출 — 합리적
판단(b2는 스킬 4단계 진입에 해당).

## 해석 주의

- 문헌 베이스라인(자발 미호출 50~56%)보다 훨씬 좋다. 원인 후보: pushy
  description 설계, 프롬프트-description 어휘 정합, **스킬이 2개뿐이라 유사
  스킬 경쟁이 없음**. n1~n3의 정답 스킬(api-spec-definition 등)이 아직 없는
  상태에서도 requirement-implementation으로 오폴백하지 않은 것은 description의
  "쓰지 않는다" 경계 조항이 작동한다는 신호.
- 이 수치가 스킬 6개 상태에서도 유지되는지가 진짜 판정이다 — Step 6에서
  같은 세트로 재측정해 전후 비교한다 (DR-004 확정 조건). 베이스라인이 이미
  높으므로 라우팅 표의 추가 가치는 "호출률 개선"보다 "스킬 증가 후 오선택
  방지"에서 나타날 것으로 예상.

## 측정에서 배운 것 (러너·감지기 보정 이력)

1. macOS에 GNU timeout 없음 → perl alarm 대체.
2. codex exec은 파이프된 stdin을 추가 입력으로 대기 → `< /dev/null` 필수.
3. **경로 문자열 grep은 3종 오탐**: git diff --stat 출력의 하네스 파일 경로 /
   레포에 커밋된 eval 문서 자체를 에이전트가 읽음 / 병렬 실행 중인 다른
   eval의 raw 파일을 읽고 그 내용이 인용됨. → 감지는 에이전트 "자신의 행동"
   이벤트만 파싱한다 (score.py).
4. 병렬 교차 오염 방지: 이후 실행은 codex도 격리 워크트리에서 돌리고,
   raw 출력은 레포 밖에 쓴 뒤 결과만 커밋하는 편이 깨끗하다.

## 부수 관찰

- codex p1: 스킬을 호출한 뒤 MOI-501을 Linear에서 못 찾자 issue-context의
  "추측으로 채우지 말고 멈춘다" 규칙대로 정지하고 사람에게 보완을 요청했다 —
  스킬 규칙이 실행에서 준수됨을 확인.
