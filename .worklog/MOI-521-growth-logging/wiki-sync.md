# MOI-521 wiki-sync

2026-10-09. 구현 전 단계. 관측 도구 결정(PostHog)과 추천안을 Wiki에 반영했다.

## 확인 범위

- PRD 성공 지표(§8): `raw/product/룸-탐색`, `회원-및-프로필`, `룸-생성`, `룸-참여-및-참여자-관리`, `룸-진행-확정`,
  `룸-진행-마무리-및-출석`, `유저-후기-및-신뢰-관리`. 지표 추천의 근거로만 썼고 PRD는 수정하지 않았다.
- 결정 근거: 관측 도구 PostHog는 사용자 전달 결정(2026-10-09). Wiki에 이 결정이 없음을 `PostHog` 검색으로 확인했다.
- 선행 문서: `sources/s-moimyeon-backend-observability-architecture`(그로스 1차 사건·SID/UTM 미결정),
  `sources/s-2026-09-09-proj-moimyeon-slack-huddle`(그로스 SaaS 보류).

## 갱신

| 주소 | 종류 | 내용 |
| --- | --- | --- |
| `raw/technical/moimyeon-growth-logging-2026-10-09` | 신규 원본 | `recommendation.md`·`tbd.md` 보존본 |
| `sources/s-moimyeon-growth-logging-recommendation` | 신규 요약 | 결정(PostHog)과 추천안 구분, 열린 결정 |
| `topics/t-모니터링-로깅-인프라` | 갱신 | 2026-10-09 변경 사항 절 추가 |
| `topics/t-moimyeon-관측성-아키텍처` | 갱신 | 그로스 분석 경로 절, 남은 확인 사항에 PostHog 전송 검증 추가 |

PRD·`policy/_src/`는 바꾸지 않았다. 제품 상태·전이 규칙 변경이 아니라 관측 도구·로깅 경로 결정이라서다.
`topics/t-moimyeon-로깅-아키텍처와-사용-가이드`는 그로스 로그 계약이 구현될 때 갱신한다(지금은 계약 미정).

## 재조회

- 적용 결과 경고 없음. 반영한 topic의 2026-10-09 절과 source 본문을 다시 읽어 확인했다.
- `wiki_lint` 오류 1건은 이번 변경과 무관한 기존 문제(`raw/product/_index`의 깨진 링크). 새 페이지에는 그래프 위치 경고만 있다.

## 남은 것

- 지표·사건 목록·`analyticsId`·리전·결제 한도 등 `tbd.md` 항목이 정해지면 같은 source/topic을 갱신한다.
- 구현 후에는 로깅 가이드 topic과 코드·테스트를 대조한다.

## 2026-10-10 결정 반영

- 근거: 팀 결정 D1~D9(`decisions.md`). 추천안 9개 항목을 모두 채택했다.
- 신규: `raw/technical/moimyeon-growth-logging-decisions-2026-10-10`(결정 보존본), `sources/s-moimyeon-growth-logging-decisions`(결정 요약).
- 갱신: `sources/s-moimyeon-growth-logging-recommendation`(결정 요약으로 연결), `topics/t-모니터링-로깅-인프라`(2026-10-10 변경 사항),
  `topics/t-moimyeon-관측성-아키텍처`(그로스 분석 경로의 확정 상태).
- 재조회: 새 source 본문을 다시 읽었고 검색에서 새 페이지 2개가 나온다. 적용 직후 검색 색인 갱신이 잠금 충돌로 실패했다는 경고가 있었으나
  검색 결과에는 반영돼 있다.
- 남은 것: `tbd.md` 1~3(FE 프로젝트 유무·리전, live 적용 시점, 처리방침 문구 담당).

## 2026-10-10 추가 결정 반영(D10·D11, D4 보충)

- 갱신: `sources/s-moimyeon-growth-logging-decisions`(남은 것 절), `topics/t-모니터링-로깅-인프라`(2026-10-10 변경 사항에 두 줄 추가).
- `topics/t-moimyeon-관측성-아키텍처`는 새로 알게 된 내용이 없어 바꾸지 않았다(적용 경고로 표시됨).
- 재조회: source의 추가 결정 절을 다시 읽어 확인했다.

## 2026-10-10 D12·D13 반영

- 갱신: `sources/s-moimyeon-growth-logging-decisions`(US 리전·필수 동의, 남은 것), `topics/t-모니터링-로깅-인프라`(2026-10-10 절 한 줄 교체). 재조회로 확인.

## 2026-10-10 D14~D17 반영

- 갱신: `sources/s-moimyeon-growth-logging-decisions`(설정값 위치·기존 회원 일괄 동의·dev 연결), `topics/t-모니터링-로깅-인프라`(dev 연결 한 줄). 재조회로 확인. 비밀값은 쓰지 않았다.

## 4단계(PR 1) 구현 대조

- 대조: `topics/t-moimyeon-로깅-아키텍처와-사용-가이드`의 출력 필드·"새 업무 사건" 절과 `GrowthEventEntry`·`GrowthEventWriter`·`LogSanitizer`·`GrowthLoggingTest`.
- 갱신: 로깅 가이드에 "그로스 사건" 절 추가(필드 규칙, 문자열 불가, INFO·표식·타입 조건, 예약 필드, 라우터 v2 제한, 테스트 프로필 주의), 출력 필드·category 문장 수정.
  `sources/s-moimyeon-growth-logging-decisions`에 구현 진행 절 추가.
- 재조회: 로깅 가이드의 새 절을 다시 읽어 확인했다.
- 남은 불일치: 없음. 라우터 v3 전달은 6단계에서 같은 절을 갱신한다.
