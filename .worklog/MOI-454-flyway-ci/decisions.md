# 구현 결정

## clean DB 전용 job을 추가하지 않고 기존 MySQL 테스트를 계약으로 사용

- 날짜: 2026-09-20
- 내용과 이유: 기본 `./gradlew test`에 포함된 `MySqlSchemaValidationIT`가 이미 빈 MySQL 8.4.9 컨테이너에 전체 Flyway migration을 적용하고 JPA 매핑을 검증한다. 같은 검증을 별도 job이나 컨테이너로 중복하지 않고, 테스트 이름과 CI 주석으로 이 계약을 명시한다.
- 대안: Flyway 전용 GitHub Actions service container와 Gradle task 추가. 같은 migration을 두 번 실행해 CI 시간만 늘어나므로 제외한다.

## 파일명 계약에서 연속 순번을 강제

- 날짜: 2026-09-20
- 내용과 이유: `V<양의 정수>__<snake_case>.sql` 형식만 허용하고 V1부터 빈 번호 없이 이어지게 한다. 같은 버전 중복뿐 아니라 앞자리 0, repeatable migration, 순번을 건너뛴 타임스탬프형 번호도 배포 전에 차단한다. 이는 `storage.md`의 "번호는 머지 순서대로 이어 붙인다"는 규칙을 실행 가능한 게이트로 만든다.
- 대안: 중복 숫자만 검사. 규칙에서 벗어난 SQL 파일을 정규식이 조용히 무시하는 기존 결함이 남아 제외한다.

## Merge Queue 대신 strict required check 사용

- 날짜: 2026-09-20
- 내용과 이유: `dev-protection` ruleset이 `build` 성공과 최신 `dev` 반영을 함께 요구한다. 선행 PR이 같은 migration 번호로 머지되면 후순위 PR은 뒤처진 상태가 되어 최신화와 CI 재실행 없이는 병합할 수 없다. 현재 병합량에서는 이 수동 최신화 비용이 Merge Queue 운영 복잡성보다 작다.
- 대안: `merge_group` 이벤트와 Merge Queue 강제. 병합량이 많은 branch의 대기와 수동 최신화를 줄이지만 현재 규모에는 과하다고 판단해 제외한다. 동시 PR 때문에 최신화·재검증이 반복적인 운영 부담이 되면 재검토한다.
