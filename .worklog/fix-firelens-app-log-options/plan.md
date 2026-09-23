# fix-firelens-app-log-options — 계획

이슈 키 없음. dev 배포 실패(Deploy AWS run 35814301696) 진단에서 나온 인프라 수정. 사용자가 "진행해"로 승인.

## 단계

- [x] 1. worktree 준비 (`fix/firelens-app-log-options`, origin/dev 기준)
- [x] 2. 컨텍스트 수집 → context.md
- [ ] 3. 변경 작성 — `application-logging` 모듈의 `app_log_configurations`에서 awsfirelens 가 Fluent Bit 로 넘기는
  Docker 드라이버 옵션(`mode`·`max-buffer-size`) 제거. 배포 입력 테스트 픽스처 정리, operations.md 기록
- [ ] 4. 정적 검증 — `terraform fmt -check` 통과, `pytest infra/terraform/tests/test_deploy_inputs.py` 17건 통과.
  `validate`·`terraform test` 는 로컬 Terraform 1.5.7 이 모듈 버전 제약보다 낮아 CI 에서 확인
- [ ] 5. plan 판독 — PR 의 CI plan 요약 코멘트. **[체크포인트: plan 승인 = apply 승인]**
- [ ] 6. 커밋·PR
