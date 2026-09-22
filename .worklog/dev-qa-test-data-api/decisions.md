# dev-qa-test-data-api — 결정

## DR-1 별도 모듈이 아니라 `core.qa` 패키지로 분리한다

- 결정: QA 전용 컨트롤러·Service·Implement 를 `io.plady.moimyeon.core.qa` 한 패키지에 두고,
  서비스 컨트롤러(`core.api.controller.v1`)에는 QA 메서드를 넣지 않는다.
- 이유: admin-api 식 런타임 조립 모듈로 빼면 RestDocs 스니펫이 core-api 의 openapi3.yaml 에
  합쳐지지 않는다(restdocs-api-spec 은 모듈 단위 snippets 디렉터리를 읽는다). QA 플랫폼은 그
  스펙 하나를 읽으므로 같은 모듈 안에서 패키지로 가르는 것이 가장 단순하다. 새 에러 코드도
  core-api 의 `ErrorCode` 체계에 들어가야 한다.
- 대안 배제: 별도 Gradle 모듈 + 스니펫 디렉터리 병합 빌드 플러밍 — 이득 대비 복잡.

## DR-2 게이트는 dev-sessions 의 상수를 그대로 쓰고, 가드 테스트로 강제한다

- `DEV_AUTH_PROFILE_EXPRESSION`("(local | local-dev | dev) & !staging & !live")을 `core.qa` 의
  모든 빈에 붙인다. 패키지 단위 `@Profile` 은 스프링에 없으므로 `QaPackageProfileGateTest` 가
  클래스패스 스캔으로 패키지 안 스테레오타입 빈 전부의 게이트를 검사한다.
- `QaTestApiProfileContextTest` 는 `DevAuthProfileContextTest` 와 같은 방식으로 프로파일별 등록·미등록을 고정한다.
- db-core 의 `QaTestDataRepository` 빈은 게이트 밖이다(모듈 경계상 상수를 못 본다). 호출자가 전부 `core.qa` 안이라
  live 에 삭제 경로는 열리지 않는다. 클래스 주석으로 남겼다.

## DR-3 삭제 쿼리는 db-core 의 `QaTestDataRepository` 한 클래스에 둔다

- 운영 Repository 15개에 `deleteAllByRoomId` 류를 흩뿌리지 않는다. 엔티티 없는 테이블
  (interview_plan·interview_round·round_assignment)은 native SQL, `review_tag`(@ElementCollection)는
  review id 를 뽑아 native 로, 나머지는 JPQL 벌크 삭제다.
- 벌크 삭제는 cascade·orphanRemoval 을 타지 않으므로 `question_vote` 는 `closing_response` 보다 먼저
  명시적으로 지운다.
- 삭제 순서(자식 → 부모)와 건수 조립은 core-api 의 `QaRoomEraser` 가 소유한다(한 커밋 단위 안의 순서는 Implement).
  꼬리질문은 parent_question_id 자기 참조라 같은 room_id 를 가지므로 question 한 문장으로 함께 지워진다.
- resume_submission.room_id·question_vote.question_id·room.title 에는 인덱스가 없어 풀 스캔이다. 수십 건 규모의
  dev 전용이라 인덱스를 추가하지 않는다(storage.md: 실측 후 근거와 함께).
- `QaTestDataRepository.findRoom` 은 `EntityManager.find` 라 soft delete 된 룸도 돌려준다(DR-5 와 짝).

## DR-4 prefix 검증: 요청 경계는 E400, 룸 판정은 E2201

- `prefix` 쿼리 파라미터가 `[QA]` 로 시작하지 않으면 요청 DTO(`QaDataRequest.toCondition`)가
  E400 으로 막는다(값 하나의 규칙 = API 스펙). 접두를 더 좁히는 것("[QA] smoke-")만 허용한다.
- 룸 하나가 QA 데이터인지의 판정(`E2201 QA_DATA_ONLY`, 409)은 쓰기 Implement 안에서만 한다.
- `hostMemberId` 는 String 으로 받아 DTO 가 파싱한다. 모델 애트리뷰트 바인딩의 UUID 변환 실패는
  `MethodArgumentTypeMismatchException` 이 아니라 바인딩 예외라 어드바이스가 E400 으로 번역하지 않는다(code-reviewer 지적).
  기존 `RoomSearchRequest` 에도 같은 공백이 있으나 이 PR 범위 밖이다(tbd.md).
- `QaDataCondition` 은 `init` 에서 표준 `require` 로 마커를 불변식으로 고정한다 — 마커 없이 지우는
  경로가 코드에 생기면 500 으로 즉시 드러난다. `QaRoomEraser.eraseGraph` 의 `check` 도 같은 목적이다.
- 로그에는 접두 원문(자유 입력)을 넣지 않고 `isNarrowed`(기본 마커보다 좁혔는지)만 남긴다(code-reviewer).
- LIKE 패턴은 `%`·`_`·이스케이프 문자를 `!` 로 이스케이프한다(MySQL LIKE 기본 이스케이프가 `\` 라 이중 표기를 피함).
- **마커는 대소문자 무시**(`startsWith(ignoreCase = true)`). dev MySQL collation 이 `*_ci` 라 `LIKE '[QA]%'` 가
  "[qa] …" 도 고르는데 코드가 구분하면 일괄 삭제의 `check` 가 500 으로 터진다(db-reviewer 지적). H2 는
  대소문자를 구분해 IT 로 재현되지 않으므로 단위 테스트로 규칙을 고정했다.

## DR-5 목록·삭제 범위는 soft delete 를 보지 않는다

- 목록은 `deleted_at` 여부와 무관하게 접두가 맞는 룸 전부를 돌려주고, 삭제도 전부 지운다.
  이 API 의 목적이 행을 없애는 것이므로 soft delete 된 QA 룸을 남겨 둘 이유가 없다.
- `counts.applications`·`counts.participants` 도 상태·soft delete 무관 행 수다(= 삭제 시 지워지는 수).

## DR-6 방장 판정은 현재 방장(HOST·JOINED·활성 참여)이다

- `hostMemberId` 필터와 회원 초기화의 "방장인 룸"은 `participation(role=HOST, status=JOINED, deleted_at null)` 행이다.
  전 방장(LEFT)은 방장이 아니다(MOI-397 의 HOST 행 보존 규칙과 일치).
- 방장이 나가 활성 HOST 가 없는 취소 룸은 목록의 `hostMemberId` 가 null 이다(응답 스펙에 optional 로 문서화).

## DR-7 회원 초기화의 삭제 범위는 요구사항 그대로 한정한다

- 방장인 `[QA]` 룸 그래프 전체, 이 회원의 participation·room_application(+ 그 자식 resume_submission),
  `[QA]` 룸에서 받은/쓴 review(+tag)·review_skip 만 지운다. 다른 회원 룸의 attendance·question 등
  이 회원의 흔적은 요구사항에 없어 남긴다(tbd.md 참고).

## DR-8 인증은 그대로 둔다

- `/v1/dev/**` 는 SecurityConfig 의 `anyRequest authenticated` 를 그대로 탄다. QA 플랫폼은
  dev-sessions 로 받은 토큰을 Bearer 로 보낸다. 요구사항이 "토큰 관련 변경 금지"이므로 permitAll 로 열지 않았다.
- **실효 인가는 없다**(qa-reviewer 필수 2). dev 에서는 `POST /v1/auth/dev-sessions`(permitAll)가 임의 회원 UUID 로
  토큰을 주므로, dev 에 닿는 누구나 남의 `[QA]` 룸을 지우고 임의 회원을 초기화할 수 있다. "본인만 초기화"
  같은 검사를 붙여도 대상 회원의 토큰을 같은 경로로 받으면 되어 보호가 되지 않아 코드로 막지 않았다.
  live 에는 경로가 없다. 수용 여부와 초기화 대상 제한(허용 목록·비QA 참여 시 거절 등)은 사람이 정한다(tbd.md).

## DR-10 테스트 격리 메모

- `QaRoomEraserIT` 의 일괄 삭제 검증은 두 번째 호출도 `[QA] smoke-` 로 좁힌다. `[QA]` 전체로 부르면 다른
  테스트가 남긴 룸 수에 결과가 흔들린다(전역 상태 가정 제거, code-reviewer 참고).
- `QaPackageProfileGateTest` 는 모든 프로파일 식을 참으로 평가하는 Environment 로 스캔한다. 스캐너가
  `@Profile` 을 평가하면 "dev 를 제외하는 엉뚱한 게이트"를 단 빈이 후보에서 빠져 가드가 놓친다(qa-reviewer 권고).
- `QaDeletedRowsTest` 는 필드를 리플렉션으로 훑어 `plus`·`total`·`toLogValues` 에서 항이 빠지면 잡는다.

## DR-11 상태 강제 대신 시작 시각 변경

- 요구사항 5번(상태 강제)은 만들지 않았다. 상태만 덮어쓰면 room_status_log(확정·시작 시각)와 출석 행이 비어
  확정 참여자 판정·진행 레일·후기 조건이 어긋난다. 위키 SSOT 는 IN_PROGRESS 를 모르고 코드는 IN_PROGRESS 를
  거치므로 전이표를 dev API 가 따로 들고 있으면 두 곳이 갈린다.
- 대신 `[QA]` 룸의 startAt 만 값 규칙 없이 바꾼다. 확정 뒤 과거로 옮기면 공개 API 의 진행 시작(출석·상태 로그 원자 저장)
  → 클로징 전원 제출 → COMPLETED → 후기까지 실제 경로로 간다. QA 충실도가 높고 dev 코드가 전이 규칙을 복제하지 않는다.
- JPQL 벌크 update 로 바꾼다(`RoomEntity.update` 는 스케줄 규칙을 다시 검증하므로 우회).

## DR-12 테스트 회원은 실제 가입 경로로 만든다

- `MemberRegistrationManager.register(GOOGLE, "qa-{uuid}", "qa-{uuid}@qa.moimyeon.test")` 를 그대로 호출한다. 닉네임
  자동 부여·필수 약관 동의·빈 프로필·닉네임 충돌 재시도가 실제와 같다. 회원 행을 직접 INSERT 하지 않는다.
- 응답에 dev 액세스 토큰(`DevAccessTokenIssuer`)을 같이 실어 QA 플랫폼이 생성 직후 바로 호출할 수 있게 한다.
  토큰 발급은 응답 조립이라 Service 가 아니라 컨트롤러가 `DevAuthController` 와 같은 방식으로 호출한다(code-reviewer 권장).
- `QaMemberCreator.create` 에는 `@Transactional` 을 붙이지 않는다. `MemberRegistrationManager.register` 가 트랜잭션 없는
  닉네임 충돌 재시도 루프이고 시도별 경계는 `MemberRegistrar` 가 갖는다. 바깥 트랜잭션을 두면 재시도가 깨진다.
- 삭제 API 는 만들지 않는다(원칙 3). 정리는 테스트 계정 초기화로 한다. 테스트 회원 식별은 이메일 도메인·providerId 접두다.

## DR-13 이력서 요약은 엔티티 전이 메서드로만 완료한다

- `ResumeEntity.completeSummary` 는 PROCESSING 에서만 허용되므로 FAILED 는 `retrySummary(now)` 로 PROCESSING 을 거쳐
  완료한다. DONE 은 그대로 둔다(덮어쓰지 않음).
- 기본 이력서 자동 지정 규칙(회원에게 기본이 없으면 지정)은 `ResumeManager.completeSummary` 와 같게 유지한다.
  `ResumeManager` 를 재사용하지 않은 이유: 시도 시각 일치 검사와 45초 타임아웃 판정이 QA 강제와 맞지 않는다.
  기본 이력서 지정 전 회원 행 락(`findForUpdateByIdAndDeletedAtIsNull`)은 실제 경로와 같게 둔다(qa-reviewer 권고).
- 다른 개념(resume)의 Repository 를 직접 쓴다 — 남의 엔티티를 내 커밋 안에서 바꾸는 경우(layers.md).
- 대상은 **이름이 `[QA]` 로 시작하는 이력서만**(E2201). 처음엔 제한하지 않으려 했으나 qa-reviewer 지적대로 요약문 조작과
  기본 이력서 지정이 목데이터 회원의 다음 신청에 영향을 준다. 룸과 같은 마커 규칙이며, 고정 테스트 계정의 이력서는
  공개 API(이름 변경)로 마커를 붙일 수 있다.

## DR-14 QA 생성 회원만 하드 삭제한다 (원칙 3 의 유일한 예외)

- "회원 행은 지우지 않는다"는 고정 테스트 계정·목데이터 회원 보호가 목적이다. 테스트 회원 생성 API 가 만든 회원은
  이 세션이 새로 만든 QA 데이터이고 지울 수단이 없으면 dev 에 누적만 된다(qa-reviewer 지적). 사용자 지시로 삭제를 연다.
- 식별은 이메일 도메인 `@qa.moimyeon.test` **와** 소셜 식별자 접두 `qa-` 둘 다 만족할 때. 아니면 E2201.
- 순서: 테스트 계정 초기화(방장인 `[QA]` 룸·참여·신청·`[QA]` 룸 후기, 비QA 방장 룸이 있으면 409) → 이 회원이 남긴 행
  (질문 작성/대상·코멘트·요약·클로징 응답(+평가)·라운드 피드백·방명록·출석·후기(전 룸)) → 회원 소유 행(이력서·프로필+관심·
  약관 동의·리프레시 토큰·웹 푸시·소셜 계정) → 회원. 한 트랜잭션.
- 남긴 행을 룸을 가리지 않고 지우는 이유: 회원 행이 사라진 뒤 남는 행은 작성자 표시(`getAttributionsIncludingWithdrawn`)
  에서 회원을 못 찾는다. QA 회원이 남긴 행은 전부 QA 데이터다.
- 질문은 id 를 먼저 뽑아(작성·대상 질문 + 그 꼬리질문) `in (:ids)` 로 지운다. 같은 테이블 서브쿼리 제약(MySQL ER 1093)을
  review_tag 와 같은 방식으로 우회해 남이 단 꼬리질문 고아를 남기지 않는다(db-reviewer 권장).
- round_assignment 는 회원 기준 삭제, interview_round.interviewee_member_id 는 null 로 비운다(엔티티 없음, native).
  감사 컬럼(attendance.recorder, room_status_log.handler, room_application.handler, participation.left_by,
  company/job_posting.created_by)은 남긴다 — 조회 경로가 회원을 역참조하지 않는다.
- 일괄 회원 삭제는 회원 한 명이 한 트랜잭션이다(Service 가 순회). 인덱스 없는 컬럼 조건 DELETE 가 많아 전원을 한
  트랜잭션에 묶으면 next-key 락이 길어진다(db-reviewer 권장). 실패한 회원 id 는 WARN 로그에 남는다.
- 이력서의 S3 객체는 지우지 않는다(앱은 S3 SDK 를 직접 부르지 않는다는 제약, 미참조 객체 정리는 기존 TODO).
- 일괄 삭제 `includeMembers` 는 룸 삭제와 별개 트랜잭션이다(각각 원자적). 기본값 false 라 기존 호출 의미는 그대로다.
  prefix·hostMemberId 와 무관하게 QA 회원 전원이 대상이다(문서에 명시, qa-reviewer 권고).
- 탈퇴(deleted_at)한 QA 회원도 지운다. 초기화의 존재 확인(`MemberFinder.getById`, 탈퇴 제외)을 `resetRows` 로 분리해
  삭제기는 `EntityManager.find` 로 존재만 보고 행 정리를 태운다(qa-reviewer 권고).

## DR-9 MySQL 계약은 Testcontainers 레인에서 고정한다

- H2 는 문자열 비교가 대소문자 구분이라 collation 규칙·native UUID 바인딩·LIKE 이스케이프를 재현하지 못한다.
  `QaTestDataRepositoryMySqlIT`(db-core, `MySqlSchemaValidationIT` 와 같은 컨테이너 구성)가 실제 MySQL 8 에서
  이 셋을 고정한다(qa-reviewer 필수 3).
