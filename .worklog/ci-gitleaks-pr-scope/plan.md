# CI Gitleaks 오탐 개선 계획

## 단계

- [x] 컨텍스트 및 변경 방향 승인 (2026-08-27)
- [x] 정적 검증·시나리오 검증 승인 (2026-08-27)
- [x] 커밋·PR 승인 (2026-08-27)

## 변경 방향

1. Docker Gitleaks를 `build`의 전체 이력 검사에서 `harness-gates`의 계산된 `GATE_RANGE` 검사로 이동한다.
2. 범위가 있으면 `detect --log-opts=${GATE_RANGE}`로 PR·push 변경분만 검사한다.
3. 새 ref처럼 범위가 없으면 `detect --log-opts=HEAD`로 HEAD에 도달 가능한 이력을 전수 검사한다.
4. `.gitleaks.toml`과 `.gitleaksignore` 경로를 명시해 container 작업 디렉터리 기본값을 제거한다.
5. `--redact --verbose`로 secret 값은 숨기면서 다음 실패에서 rule·파일·commit 위치를 진단 가능하게 한다.
6. 게이트 self-test에 workflow 계약 검사를 추가해 전체 이력 검사 회귀와 설정·ignore 경로 누락을 차단한다.
7. Gitleaks를 실행하는 checkout도 기존 build와 동일한 full SHA pin으로 고정한다.

## 영향

- 대상: PR 및 main·dev push CI
- Terraform plan: 해당 없음
- AWS·live 자원 변경: 없음
- apply: 없음

## 검증 결과

- YAML parse·shell syntax·diff whitespace: 통과
- 하네스 gate self-test 21개: 통과
- Gitleaks 변경 범위 검사: 누출 0건
- Gitleaks 새 ref HEAD-history 검사: 누출 0건
- 합성 토큰 양성 검사: finding 발생 확인
- GitHub Actions 정책: `contents: read` 유지, checkout·외부 image digest pin 유지, 신규 secret·write permission 없음
- GitHub dev 보호 설정: required status check·ruleset 없음, job 이동에 따른 기존 필수 체크 약화 없음
- QA 재리뷰: PASS, 필수·CONDITIONAL 0건
