# MOI-593 결정 기록

## D-1: PR 제목·본문 수정에서는 배포와 무관한 job만 건너뛴다

- 결정: `edited`가 base 변경이 아니면 `monitoring-smoke`만 건너뛴다. 필수 체크 `build`, 후보 이미지(`image-scope`·`image`),
  `harness-gates`는 계속 실행한다. harness-gates를 건너뛰면 시크릿 검사 실패가 PR 화면에서 "건너뜀"으로 가려진다(public 저장소,
  qa-reviewer 권고). 다시 도는 비용은 Gradle build cache(D-3)와 이미지 레이어 캐시로 줄인다.
- 처음 안(모든 job 건너뛰기 + 배포 검증이 skipped 실행 무시)을 버린 이유(qa-reviewer BLOCK):
  - GitHub은 건너뛴 job을 필수 체크 통과로 보고, 같은 head의 가장 최근 check run을 쓴다. 실제 build가 도는 중에 CodeRabbit이
    본문을 고치면(PR #186에서 1~2분 뒤) 결과가 나오기 전에 머지 버튼이 초록색이 된다.
  - 같은 실행의 image가 건너뛰어지면 배포가 후보를 찾지 못해 매번 직접 빌드한다.
  - 배포 검증 스크립트를 바꿀 필요가 없어져 `verify-pr-ci.sh`는 그대로 둔다(최신 실행만 보는 기존 동작 유지).
- 계약 테스트가 build·harness-gates·image 쪽에 edited 조건이 들어오면 실패한다.
- 범위 밖: `review-swarm.yml`도 `edited`에 반응해 LLM 리뷰를 다시 돈다. 하네스 소관이라 이번에 바꾸지 않는다.

## D-2: 문서만 바뀐 PR은 후보 이미지를 만들지 않는다

- `image-scope` job이 PR 머지 커밋과 첫 부모(base)의 차이를 `.github/scripts/runtime-changes.sh`로 판정한다. 규칙은 dev 배포·live
  승격의 문서 제외 규칙(DR-005)과 같다. 그 규칙들을 이 스크립트로 모으는 것은 MOI-592.
- 스크립트를 `infra/terraform/scripts`가 아니라 `.github/scripts`에 둔다. `infra/terraform/` 아래 변경은 Terraform 경계 대기를
  만든다(PR #186 머지 후 dev 배포가 Terraform Apply를 기다린 원인).
- 이미지 job은 검증과 병렬로 둔다(이슈의 [결정 필요]). 검증 뒤로 옮기면 PR CI 끝나는 시간이 이미지 시간만큼 늘고, 후보는
  어차피 같은 실행의 build 성공에 묶여서 안전성 차이가 없다.

## D-3: Gradle build cache를 켜고 Testcontainers 모듈의 test는 뺀다

- `org.gradle.caching=true`. setup-gradle이 dev push CI 실행의 로컬 캐시를 저장하고 PR 실행이 읽는다.
- 테스트 클래스패스에 Testcontainers가 있는 모듈(지금은 db-core, redis-core, core-worker, redis-api-adapter)의 Test 태스크는
  루트 `build.gradle.kts`가 자동으로 캐시에서 뺀다(새 모듈도 빠뜨리지 않게, qa-reviewer 권고). 외부 컨테이너 상태는 Gradle 입력에 잡히지 않는다.
- 알려진 제약: 실제 시각에 의존하는 테스트는 입력이 같으면 다시 돌지 않는다(operations.md 2026-08-18 같은 사례는 코드가
  바뀌는 PR이나 캐시 만료 때 드러난다).
