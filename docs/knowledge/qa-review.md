# QA 리뷰 — 변경 위험 분석

> 출처: 전문 에이전트 블루프린트(팀 내부, `_workspace/harness-reference/specialist-agents-blueprint.md`
> §5 QA Agent)에서 머지 전 정적 리뷰에 해당하는 부분을 벤더링. BOLA/BFLA는
> OWASP API Security Top 10 원전. 성능 게이트(k6·SLO 연동)는 SLO 도입
> 시점까지 보류(.worklog/MOI-474 plan의 SLO 예약 항목).
>
> 이 문서는 특정 에이전트에 종속되지 않게 작성한다 — 현재 소비자는
> `.agents/agents/qa-reviewer.md`(머지 전 diff 리뷰)이고, 팀 개발 플랫폼에
> qa-engineer가 배치되면 그쪽으로도 이식한다.

## 변경 영향 분류

diff를 다음 사슬로 분류한다. 변경이 사슬의 위쪽(노출면)에 가까울수록
아래층까지의 영향을 추적한다.

```text
Controller/API → 인증/인가 → Service/도메인 로직 → 트랜잭션
  → Repository/쿼리 → MySQL 스키마/인덱스 → 외부 API → 런타임/인프라
```

## 위험 신호 — 하나라도 해당하면 검증 요구를 확장한다

- 금전·주문·회원·권한 관련 변경
- 데이터 쓰기 또는 삭제
- 트랜잭션 범위 변경
- DB 마이그레이션 동반
- 재시도·비동기 처리
- 외부 API 계약 변경
- 대량 조회·검색·pagination
- 장애 시 fallback 변경
- 라이브러리·프레임워크 의존성 변경 — 버전 업그레이드 포함
  (멘토팀 실장애 사례, 2026-08)

## 특수 검증 8문항 — 위험 신호와 무관하게 해당 유형 변경엔 반드시 묻는다

1. `POST` 재시도 시 중복 데이터가 생기지 않는가.
2. deadlock·lock timeout 재시도 시 중복 side effect가 생기지 않는가.
3. 타 사용자 ID로 조회·수정·삭제가 가능한가 (BOLA — OWASP API #1).
4. 관리자 API를 일반 사용자가 호출할 수 있는가 (BFLA — OWASP API #5).
5. nullable 필드 추가·삭제가 구버전·신버전 애플리케이션 모두에서
   동작하는가 (롤링 배포 중 두 버전이 공존한다).
6. 캐시·외부 의존이 비어 있거나 unavailable이어도 원본 저장소에서 복구
   가능한가.
7. pagination 정렬이 안정적인가 (동률 시 순서가 요청마다 바뀌지 않는가).
8. timeout과 retry의 곱으로 요청 시간이 지나치게 늘어나지 않는가.

## 검증 증거의 원칙

- MySQL 의미론을 검사하는 테스트에서 H2 같은 대체 DB를 정답으로 간주하지
  않는다 — 운영과 동일한 MySQL major version(Testcontainers) 기준.
  (우리 테스트 규칙: `docs/conventions/testing.md`)
- 인가 테스트에는 부정 케이스(negative test)가 반드시 포함되어야 한다 —
  "권한 있는 사용자가 된다"만 확인하는 테스트는 BOLA/BFLA를 잡지 못한다.

## 우리가 겪은 것

아직 없다. 사고·리뷰에서 배운 것이 생기면 날짜와 함께 한 줄 추가한다
(형식: README의 "어떻게 늘리는가").
