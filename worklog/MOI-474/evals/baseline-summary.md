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

---

# 트리거 베이스라인 — Step 3 신규 3종 (스킬 5개 경쟁 상태, 라우팅 등록 전)

- 날짜: 2026-08-23. 스킬 5개가 공존하는 **첫 경쟁 측정** — 3자 경계·동음이의
  near-miss가 시험 대상. 세트: `.agents/evals/trigger/{api-spec-definition,
  api-connection,entity-design}.tsv` (각 양성 5·음성 5·경계 2)
- 실행: claude 2.1.239 (N=2), codex-cli 0.149.0 (N=1, 격리 워크트리 — 교훈 5
  반영). 240초 kill 워치독. 원본: `trigger-{스킬}-{런타임}-20260823-*.csv`
- **entity-design codex는 6/12에서 세션 중단으로 부분 측정** (양성 5 + 음성 1).
  DR-017(eval 경량화)에 따라 잔여 6건은 재실행하지 않는다.

## 확정 결과 (score.py 5-스킬 교차 재집계)

| 스킬 | claude 양성 (N=2) | claude 음성 오호출 | codex 양성 (N=1) | codex 음성 오호출 |
| --- | --- | --- | --- | --- |
| api-spec-definition | 10/10 | 0/10 | 5/5 | 0/5 |
| api-connection | 10/10 | 0/10 | 5/5 | 0/5 |
| entity-design | 7/7 유효* | 0/1 유효* | 5/5 | 0/1 (부분) |

*entity-design claude는 24건 중 19건이 **세션 사용량 한도로 실행 자체가
안 됐다**(p3-2부터 한도 응답만 기록 — 교훈 8). 유효 측정은 양성
p1·p2(N=2)+p3(N=1) 5건 + 한도 해제 후 델타 재측정(20260823-2111-delta,
p4·p5·n3 각 N=1) 3건. 결과: 양성 7/7 호출, n3(인덱스 하나만 추가) 미호출
— 정답. 나머지 음성·경계 무효분은 DR-017에 따라 재실행하지 않는다
(음성 커버는 codex n1 + 타 스킬 세트의 교차 라우팅 실측으로 부분 확보).

- **유사 스킬 오선택(LangChain ~20개 스킬 실험의 우려) 미발생**: 유효 양성
  전건에서 다른 스킬을 잘못 집은 사례 0. near-miss 음성은 오히려 정답
  스킬로 정확히 교차 라우팅됐다 — api-spec n1(구현해줘)→req-impl,
  n2(연결해줘)→api-connection, n3(엔티티 모델링)→entity-design 등 6/6.
- **정정(2026-08-23 재측정)**: 최초 집계의 "claude entity-design p4·p5
  미호출"은 트리거 실패가 아니라 세션 한도 오염이었다. 한도 해제 후
  p4·p5를 N=1 재측정한 결과 기존 description 그대로 둘 다 호출 —
  **description 보강 불필요**. 5스킬 전체에서 실제 트리거 실패는 0건.
- 경계: claude는 다단계 요청(b1 "테이블부터 API까지"/"스펙부터 연결까지")에서
  issue-context를 먼저 호출하는 일관 패턴 — 워크플로우 스킬의 1단계가
  issue-context 경유이므로 설계 의도와 부합. codex는 경계에서 스킬 파일
  여러 개를 읽는 탐색 행동을 보임(호출 판정의 노이즈, 오선택 아님).

## 비용 관찰 (DR-017의 근거)

이번 라운드는 3스킬 × 12프롬프트 × (claude 2회 + codex 1회) = **102개
콜드 세션**을 한 번에 돌렸다 — 이전 라운드(스킬당 36세션)의 3배 배치.
케이스 단건이 아니라 풀 매트릭스 구조가 토큰 소모의 원인이다.
raw 트랜스크립트만 21MB. 이후 eval은 DR-017 경량 프로토콜을 따른다.

## 추가 교훈

8. **세션 사용량 한도 응답은 미호출과 구분해야 한다** — 한도에 걸린 claude
   세션은 "You've hit your session limit" 한 줄만 남기고 즉시 종료되는데,
   호출 패턴이 없어 detected=no로 집계됐다(entity-design claude 19/24건
   오염). run.sh·score.py에 LIMIT 판정을 추가했다. 배치가 길수록 한도에
   걸릴 확률이 높아진다 — DR-017 경량화가 측정 유효성 면에서도 이득.

---

# 트리거 베이스라인 — ship-pr (스킬 6개 경쟁 상태, DR-017 경량 프로토콜 첫 적용)

- 날짜: 2026-08-24. claude N=1 전체 12건 + codex 스모크 양성 2건만 실행
  (DR-017). 격리 워크트리(HEAD 1f78159f). 원본:
  `trigger-ship-pr-claude-20260824-1503.csv`, raw `ship-pr-codex-*-smoke/`.

## 확정 결과 (score.py 6-스킬 교차 재집계)

| | 양성 | 음성 오호출 | 경계 |
| --- | --- | --- | --- |
| claude (N=1) | 5/5 | 0/5 | 아래 |
| codex 스모크 (N=1) | 2/2 | — | — |

- n1(구현해줘)→requirement-implementation 정확 교차 라우팅, n2~n5 무호출.
  LIMIT 판정 0건.
- 경계 b1("MOI-735 마무리하자"): 스킬 호출 없이 이슈 조회부터 시도 — 모호
  요청에 컨텍스트 수집으로 진입, 오폴백 없음. b2("테스트 통과했으면 바로
  올려줘"): 워크트리를 검사해 "올릴 변경이 없다"며 정지·보고 — ship-pr
  1단계(변경분 검토)의 의도와 일치하는 판단. 미푸시 타 브랜치를 발견하고도
  임의 push하지 않음.

---

# 트리거 베이스라인 — Step 4b 3종 (스킬 9개 경쟁 상태, 축소 세트)

- 날짜: 2026-08-24. 저빈도 워크플로우라 축소 세트(양성 3·음성 3·경계 2 =
  8행, DR-023) × claude N=1 + codex 스모크 양성 각 1건. 격리 워크트리
  (HEAD 2a0b02d0). 원본: `trigger-{스킬}-claude-20260824-*.csv`,
  raw `*-codex-*-smoke/`.

## 확정 결과 (score.py 9-스킬 교차 재집계)

| 스킬 | claude 양성 | claude 음성 오호출 | codex 스모크 |
| --- | --- | --- | --- |
| prompt-change | 3/3 | 0/3 | 1/1 |
| infra-change | 3/3 | 0/3 | 1/1 |
| incident-response | 3/3 | 0/3 | 1/1 |

- 9-스킬 경쟁에서 오선택 0. 교차 라우팅 정확: 각 세트의 "구현해줘"→
  requirement-implementation(3/3), infra n3("서버 왜 죽었어")→
  **incident-response** (신규 3자 경계 정확), incident n1(느린 쿼리)→
  무호출(db-reviewer는 위임이라 정답).
- 경계: incident b1(요약 빈 문자열)→incident-response — 설계한 정답
  경로(진단 후 prompt-change 재투입). infra b1(배포 시간 줄여보자)→
  issue-context — 다단계 요청의 일관 패턴. infra b2(plan 결과 봐줘)→
  무호출(판독만 직접 수행 — 부분 작업이라 합리). prompt-change b1(요약이
  이상한 말)→prompt-change — incident와 양쪽 다 합리인 경계.
- **차분 보강 1건**: prompt-change b2("오타 고쳐줘") 미호출 관찰 →
  "사소한 수정도 출력에 영향" 절을 description에 추가 후 해당 1건만 N=1
  재측정, INVOKED 확인 (DR-017 차분 재측정의 첫 적용, raw
  `prompt-change-claude-*-delta/`). 핵심 규칙("오타 수정도 eval 비교")의
  보호 지점이라 경계임에도 보강했다.
