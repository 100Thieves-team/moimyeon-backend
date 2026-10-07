# MOI-454: Flyway 충돌 방지 CI

## 이슈 요약

- Linear: https://linear.app/100-thieves/issue/MOI-454/flaway-%EC%B6%A9%EB%8F%8C-%EB%B0%A9%EC%A7%80-ci-%EC%B6%94%EA%B0%80
- 동시에 작업한 두 PR이 같은 Flyway 버전을 사용해도 Git 파일 충돌이 나지 않아, 병합 뒤 애플리케이션 부팅에서야 실패하는 문제를 CI에서 차단한다.
- 순번형 `V<번호>__<설명>.sql`을 유지하고 `outOfOrder`나 타임스탬프 버전은 사용하지 않는다.
- PR에서 파일명·버전 계약과 빈 MySQL 전체 migration을 검증하고, `dev`의 strict required check가 최신 base 반영 후 재검증을 강제한다.
- 이슈에는 별도 PRD·위키 링크, 댓글, 하위 이슈, 첨부가 없다.

## 현재 코드

- `.github/workflows/ci.yml`: PR·push CI에서 숫자 버전 중복을 JDK 설치 전에 검사하지만, 규칙에서 벗어난 SQL 파일명은 무시한다.
- `storage/db-core/src/test/kotlin/io/plady/moimyeon/storage/db/MySqlSchemaValidationIT.kt`: 기본 `test`에 포함되는 MySQL 8.4.9 Testcontainer를 빈 DB로 띄우고 Flyway 전체 migration과 JPA validate를 수행한다.
- `storage/db-core/src/test/kotlin/io/plady/moimyeon/storage/db/RoomStatusLogMigrationIT.kt`: 별도 MySQL 컨테이너에서 V26까지 적용한 뒤 최신 migration으로 올리는 호환성 시나리오를 검증한다.
- `docs/conventions/storage.md`: migration 번호는 머지 순서대로 이어 붙이고, 브랜치가 겹치면 뒤에 머지되는 쪽이 번호를 올린다고 규정한다.
- GitHub `dev` 브랜치에는 2026-09-20 `dev-protection` ruleset이 활성화됐다. `build`가 필수 검사이고 strict 정책으로 최신 `dev` 반영을 요구하며 bypass actor는 없다.

## 변경 경계

- migration 파일명·연속 순번·중복 버전을 검사하는 결정론적 CI 게이트와 자체 테스트를 추가한다.
- 기존 MySQL Testcontainer 검증을 재사용하며 별도 DB 컨테이너나 중복 migration job을 추가하지 않는다.
- Merge Queue는 도입하지 않는다. 동시 병합량이 높아져 수동 최신화와 CI 재실행이 반복적인 운영 부담이 될 때 재검토한다.
- Terraform·AWS·애플리케이션 런타임·DB schema와 migration 내용은 변경하지 않는다.
- GitHub ruleset 변경 자체는 코드 diff에 포함하지 않고, 읽기 전용 API 조회 결과를 worklog에 기록한다.
