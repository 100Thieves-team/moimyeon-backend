# dev-qa-test-data-api — 미결정

- **인증 방식**: `/v1/dev/**` 는 현재 `authenticated`(dev 세션 토큰 필요). QA 플랫폼이 토큰 없이
  호출해야 한다면 SecurityConfig 에 `permitAll` 추가가 필요한데, 요구사항의 "토큰 관련 변경 금지"와
  충돌하므로 사람 결정 필요. 미인증 호출은 live 에서도 404 가 아니라 401 이다(빈 부재와 무관한 필터 계층).
- **인가 수용 여부**: dev 에서 인증 회원이면 누구나 남의 `[QA]` 룸 삭제·임의 회원 초기화가 가능하다(DR-8).
  dev-sessions 가 임의 회원 토큰을 주는 구조라 코드 검사로는 못 막는다. 수용할지, 초기화 대상을 SSM 의 테스트 계정
  허용 목록으로 제한할지 사람 결정 필요(qa-reviewer).
- **회원 초기화가 비QA 룸의 참여·신청 행도 지운다**(요구사항 4번 그대로). 그 룸의 확정 참여자 판정·전 방장
  이력이 이 회원에 한해 깨진다(db-reviewer 권장). QA 룸으로만 좁힐지 결정 필요 — 좁히면 원칙 3 과 더 맞다.
- **회원 초기화의 잔여 흔적**: 다른 회원의 비QA 룸에 남는 이 회원의 attendance·question·closing_response·
  round_feedback·guestbook_post 는 요구사항 범위 밖이라 남긴다. "처음 상태"를 더 넓게 볼지 결정 필요.
- **모델 애트리뷰트 바인딩 실패 → 500**: `RoomSearchRequest` 처럼 생성자 바인딩 DTO 의 타입 변환 실패는
  `ApiControllerAdvice` 에 핸들러가 없어 E500 으로 샐 가능성이 있다(code-reviewer). index.adoc 의 "타입 불일치 → E400"
  규약과 어긋나므로 별도 이슈로 어드바이스 핸들러 추가 검토.
- **룸 상태 강제**: 시작 시각 변경으로 대체(DR-11). 그래도 상태 덮어쓰기가 필요하면 위키 SSOT 에 IN_PROGRESS 를
  먼저 반영해야 한다(`topics/t-moimyeon-ssot-code-sync-audit`).
- **테스트 회원 누적**: 생성 API 는 있고 삭제는 없다. dev 의 회원 수가 늘어나면 정리 정책(이메일 도메인 기준 일괄 비활성) 결정 필요.
- **엔티티 없는 테이블**: interview_plan·interview_round·round_assignment 는 코드에 쓰는 곳이 없어
  dev 에서 항상 0 건일 가능성이 높다. 삭제 경로는 완결성을 위해 두었다(native SQL).
