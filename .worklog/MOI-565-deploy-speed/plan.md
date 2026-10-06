# MOI-565 배포 속도 개선 — plan

- [ ] 1. worktree 준비 — `chore/MOI-565-deploy-speed`
- [ ] 2. 컨텍스트 수집 — context.md
- [x] 3. 방향 결정 — decisions.md D-1~D-5 (2026-10-06 사용자 승인)
- [ ] 4. 변경 작성
  - PR-A (Terraform, 먼저 머지): candidate ECR·PR 이미지 역할·배포 설정 SSM, dev 최소 4대·ALB 10초,
    변수 동기화 init 1회, 적용 SHA 기록(SSM·GitHub 변수)
  - PR-B (워크플로, PR-A 적용 뒤): dev push 배포 + PR CI 트리 검증, PR CI 이미지 빌드, Terraform 생략,
    SSM 설정 읽기, 계약 테스트·문서
- [ ] 5. 대상별 검증
- [ ] 6. Terraform plan 판독 (PR-A의 CI plan) — 머지 승인 = apply 승인
- [ ] 7. 커밋·PR (ship-pr)

## 실행한 검증 (PR 전)

- Terraform 1.15.9 `fmt -check`, `validate` dev·live 통과(기존 경고 1건: redis_ecs.tf deprecated 인자).
- actionlint 1.7.12 전체 워크플로 통과.
- `infra/terraform/tests/*.sh` 8종(PR-A), PR-B의 신규 시나리오 테스트 포함 9종, `test_deploy_*.py` 17건 통과.
- 신규 `deploy-source-verification-test.sh`: 트리 불일치·build 실패/누락·PR 없음·다른 브랜치 거부,
  앱 전용·테스트 전용 커밋 즉시 통과, 미적용 인프라 뒤 커밋 대기 확인.
- 하네스 게이트(lint_skills, check_pairings, scan_secrets) 통과.
- 로컬 Terraform plan 미실행(AWS 자격증명 없음) — PR-A의 CI plan 요약으로 판독한다.
