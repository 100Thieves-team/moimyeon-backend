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
5. codex `--sandbox read-only` 지정에도 메인 트리에 파일이 생성됐다
   (n2 실행이 `worklog/MOI-507/plan.md` 작성 — 잔여물은 raw/stray-MOI-507로
   이동). 원인 미상 — read-only 라벨을 신뢰하지 말고 **codex도 격리
   워크트리 필수**. 부수적으로, 그 plan.md는 worklog 계약(계획·차단 기록·
   에스컬레이트)을 정확히 따랐다.

## 부수 관찰

- codex p1: 스킬을 호출한 뒤 MOI-501을 Linear에서 못 찾자 issue-context의
  "추측으로 채우지 말고 멈춘다" 규칙대로 정지하고 사람에게 보완을 요청했다 —
  스킬 규칙이 실행에서 준수됨을 확인.

---

# 트리거 베이스라인 — issue-context (라우팅 등록 전)

- 날짜: 2026-08-21. 조건·방법은 위와 동일하되 **런타임별 격리 워크트리 분리**
  (교훈 4 반영). 세트: `.agents/evals/trigger/issue-context.tsv`.
- 원본: `trigger-issue-context-{claude,codex}-20260821-1841.csv`

## 확정 결과 (score.py)

| | 양성 | 음성 오호출 | 경계 |
| --- | --- | --- | --- |
| claude (N=2) | 10/10 (100%) | 0/10 (0%) | b1: req-impl 정확 라우팅(아래), b2 2/2 호출 |
| codex (N=1) | 5/5 (100%) | 0/4 유효 (n4 측정 무효, 아래) | 2/2 호출 |

- claude b1("MOI-606 구현해줘"): issue-context 직접 호출 없이
  requirement-implementation을 정확히 선택 — 원하는 경계 판별. issue-context
  미도달은 max-turns 4 상한 때문(경유 호출은 2단계라 4턴 안에 못 감).
- codex n4(원문 "하네스 커밋 요약"): 스킬 파일을 **과제 내용으로** 읽어
  경로 기반 감지와 충돌 — 측정 무효 처리하고 프롬프트를 하네스 무관
  소재("room 도메인 커밋 요약")로 교체했다.

## 추가 교훈

6. perl alarm은 SIGALRM을 무시하는 프로세스를 못 죽인다 — codex b1이 240초
   상한을 넘겨 92분(5,533초) 실행됐다. run.sh를 kill 워치독으로 교체.
7. 음성(near-miss) 프롬프트가 하네스 자체를 소재로 삼으면 경로 기반 감지와
   충돌한다 — 음성 세트는 하네스와 무관한 소재로 설계한다.
