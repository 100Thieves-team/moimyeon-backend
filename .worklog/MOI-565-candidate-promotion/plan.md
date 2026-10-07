# MOI-565 후속: 후보 이미지 승격 수정 — plan

결정·배경은 `.worklog/MOI-565-deploy-speed/`를 따른다. #161 머지 후 dev push 배포(run 37554438607)의 승격 실패 수정이다.

- [ ] 1. 원인 확인
  - API: 재현 가능한 빌드로 이전과 같은 digest가 되어 ECR push 시각이 첫 push 시각으로 남음 → "CI 실행보다 오래됨" 오판
  - Worker: `provenance: false` 단일 manifest를 `imagetools create`가 index로 감싸 digest 변경(로그: 613fe7fd → 8fa96b09)
- [ ] 2. 수정 — push 시각 검사 대신 CI 실행 나이(13일) 검사, CI 후보 빌드는 provenance 기본값(index)
- [ ] 3. 검증 — 계약 테스트에 두 실수 방지 단언 추가, actionlint, 날짜 계산 확인
- [ ] 4. PR

## 실행한 검증

- `infra/terraform/tests/*.sh` 9종, `test_deploy_*.py` 17건, actionlint 통과.
- GNU date로 실행 나이 계산 확인(오늘 실행은 FRESH).
- 로컬 레지스트리 재현은 실행 권한이 없어 하지 못했다. 근거는 배포 로그의 digest 변화와, provenance index 이미지를 같은 스크립트로 승격해 온 기존 배포 성공 이력.
