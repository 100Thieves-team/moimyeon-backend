# MOI-454 Flyway 충돌 방지 CI 계획

## 단계

- [x] 컨텍스트 및 구현 착수 승인: 사용자 "진행해줘" (2026-09-20)
- [x] 정적 검증·시나리오 검증 및 인프라 plan 승인: 사용자 "지정했어. 다음 단계 진행해줘" (2026-09-20)
- [x] 커밋·PR 승인: 사용자 "승인할게" (2026-09-20)
- [x] GitHub dev strict required-check ruleset 활성화 확인 (2026-09-20)

## 변경 방향

1. migration 디렉터리의 SQL 파일이 `V<연속 순번>__<snake_case 설명>.sql` 형식인지 검사한다.
2. 동일 숫자 버전이 둘 이상이면 충돌 파일을 모두 표시하고 실패한다.
3. 순번 누락·앞자리 0·타임스탬프형 번호를 파일명 계약 위반으로 차단한다.
4. 검사기를 Python 표준 라이브러리만으로 작성하고 양성·음성 fixture 자체 테스트를 CI에서 함께 실행한다.
5. 기존 `MySqlSchemaValidationIT`에 빈 MySQL 전체 migration 완료 계약을 명시하되 새 컨테이너는 추가하지 않는다.
6. GitHub `dev-protection` ruleset에서 `build`를 필수 검사로 지정하고 최신 `dev` 반영 후 재검증을 강제한다. Merge Queue는 사용하지 않는다.

## 영향

- 대상: PR, dev·main push CI와 GitHub `dev` 병합 정책
- Terraform plan: 해당 없음
- AWS·live 자원 변경: 없음
- DB schema·migration 변경: 없음
- apply: 없음

## 검증 계획

- migration 검사기 단위 테스트와 현재 migration 디렉터리 검사
- 중복 버전, 잘못된 파일명, 순번 누락, 타임스탬프형 버전 실패 확인
- `MySqlSchemaValidationIT` 실행으로 빈 MySQL 전체 migration 확인
- 전체 `./gradlew test ktlintCheck`
- workflow YAML parse 및 actionlint
- GitHub Actions 최소 권한·SHA pin·배포 종속 불변식 대조

## 구현·검증 결과

- migration 계약 검사기: 현재 V1~V27 통과.
- 검사기 자체 테스트 7개 통과: 정상 연속 순번, 중복 version, 잘못된 파일명, 순번 누락, 타임스탬프형 version, 중첩 디렉터리, SQL 파일 없음.
- `MySqlSchemaValidationIT`: 빈 MySQL 8.4.9 전체 migration 및 JPA validate 통과(17초).
- `./gradlew test ktlintCheck`: 방향 변경 후 재검증 통과(205 tasks, 53초).
- actionlint 1.7.12: release checksum 검증 후 `ci.yml` 통과.
- 하네스 gate self-test, skill lint, config profile sync, diff whitespace: 통과.
- 최종 읽기 전용 code-reviewer: PASS, 필수·권장 지적 없음.
- 최종 읽기 전용 qa-reviewer: PASS, 위험도 low, coverage gap 없음.
- Terraform plan: 해당 없음. AWS·live 자원·권한·DB schema 변경 없음.
- GitHub ruleset `dev-protection`: active, target `refs/heads/dev`, required check `build`, strict=true, bypass actor 없음.

검증한 커밋: 208c901a488e2ee4a49dcfe813cdbdb521d9fef7
