# MOI-592 계획

[MOI-592](https://linear.app/100-thieves/issue/MOI-592) 앱 코드 없는 커밋은 앱 재배포 생략. 상위 MOI-584 과제 4.
사용자가 MOI-584 서브이슈 처리·PR·머지를 위임(2026-10-10).

- [x] 1. 컨텍스트 — "무엇을 배포에서 뺄지"가 deploy-aws 2곳, promote-live 4곳, ci(MOI-593) 1곳에 따로 있었다.
  PR #186 머지 후 dev 배포가 `infra/terraform/scripts` 변경 때문에 Terraform Apply를 약 10분 기다렸다.
  Terraform 모니터링 모듈이 `infra/observability`를 읽는데 적용 생략 판단은 그 경로를 보지 않는 것을 발견.
- [x] 2. 변경 — `.github/scripts/runtime-changes.sh <dev|live>` 하나로 모으고 제외 경로 확대, Terraform 입력에 `infra/observability` 추가
- [x] 3. 검증 — 규칙 단위 테스트(신규), 배포·릴리스 계약 테스트, 경계 대기 테스트(수정 전 스크립트에서 실패 확인), actionlint, shellcheck,
  qa-reviewer PASS(권고 6건 반영)
- [ ] 4. 커밋·PR·머지 후 하네스 전용 커밋의 dev 배포 생략 확인
