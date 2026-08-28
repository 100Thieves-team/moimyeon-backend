# MOI-484 `$ref` 이름 오탐 수정 계획

검증한 커밋: 250ba727

## 승인 체크포인트

- [x] 원인과 변경 범위 승인 (2026-08-27: 사용자 진행 요청)
- [x] 비교기 수정·정적 검증·인프라 plan 판독 승인 (2026-08-28: 사용자 실행 요청)
- [x] 커밋·PR 승인 (2026-08-28)

체크박스는 사람이 승인한 뒤에만 표시한다.

## 자연어 흐름

1. operation이 참조하는 로컬 component를 실제 계약 내용으로 확장한다.
2. component 이름만 바뀌고 확장 결과가 같으면 변경으로 알리지 않는다.
3. 확장한 스키마 내용이나 재귀 참조 구조가 달라지면 해당 operation을 변경으로 알린다.
4. 외부 `$ref`는 이 문서만으로 해석할 수 없으므로 기존처럼 참조 문자열을 계약으로 유지한다.

## 구현 순서

1. 동일 schema의 component rename과 recursive component rename을 무변경으로 고정하는 실패 테스트 추가
2. 실제 schema 내용 변경·재귀 연결 대상 변경을 계속 감지하는 양성 테스트 보강
3. 로컬 `$ref` 이름 대신 확장 내용과 이름 독립적인 cycle 위치로 fingerprint 생성
4. 실제 gh-pages `9c3b3ba1 → c9e21647` 비교가 64건에서 `POST /v1/auth/dev-sessions` 1건으로 줄어드는지 확인
5. incident 교훈과 결정 기록 갱신

## 검증

- OpenAPI operation 비교 단위 테스트
- 실제 공개 스펙 두 버전 회귀 비교
- API 스펙 알림 정적 계약 검사
- actionlint, 하네스 게이트, 시크릿 스캔, `git diff --check`
- `./gradlew test ktlintCheck`

현재 실행 결과:

- OpenAPI operation 비교 단위 테스트 28개 통과
- 일반·재귀 component rename 무변경 확인
- 일반·재귀 pure local-ref alias 무변경, 순환 alias 비교 실패 확인
- alias 계약 sibling 변경 감지, description-only sibling·alias rename 무변경 확인
- 재귀 참조 대상과 실제 schema 내용 변경 감지 확인
- 사고 당시 gh-pages `9c3b3ba1 → c9e21647` 비교 결과: 64건에서 `POST /v1/auth/dev-sessions` 1건으로 정정
- actionlint 1.7.12, API 알림 계약, 하네스 self-test·스킬 lint·시크릿 스캔·`git diff --check` 통과
- `./gradlew test ktlintCheck` BUILD SUCCESSFUL, 기존 Redis 컨테이너는 테스트 후 원래 중지 상태로 복원
- code-reviewer 최종 통과: 필수·권장 지적 없음
- qa-reviewer 최종 PASS: finding·coverage gap 없음
- Terraform 파일 변경 없음: `0 add / 0 change / 0 destroy`

## 영향

- GitHub Actions의 API 스펙 변경 판정만 수정한다.
- gh-pages 문서 내용, Slack webhook, 애플리케이션 런타임, AWS·Terraform 리소스는 변경하지 않는다.
- Terraform plan: 파일 변경 없음, 예상 `0 add / 0 change / 0 destroy`.
