# deploy-boundary-check-once 계획

이슈 키 없는 작업. 2026-10-11 dev 배포 실패(incident-response) 후속 수정.

체크는 사람이 승인한 단계에만 한다.

- [x] 1. 장애 진단과 완화(재실행)는 사람 승인·실행으로 끝남. 복구 확인: 재실행 성공, dev `/v1/terms`가 PRIVACY v1.2 응답
- [ ] 2. 수정: `wait-for-terraform-boundary.sh`가 마감 전에 최소 1회 확인. 회귀 테스트(DEBUG trap 시계)와 운영 교훈 기록
- [ ] 3. PR

## 타임라인

| 시각(UTC) | 내용 |
| --- | --- |
| 10-11 09:26:46 | Deploy AWS prepare: `Terraform source matches applied dev revision 9e69ab50` |
| 10-11 09:26:57.98 | deploy 잡 lock 안 확인 시작(대기 1초) |
| 10-11 09:26:58.000 | 확인 없이 `Timed out waiting for Terraform Apply` 실패. 서비스 교체 전 단계라 dev는 직전 배포 유지 |
| 10-11 09:31 | 사람이 실패 잡 재실행. 경계 확인 통과 후 배포 성공 |
| 10-11 09:41 | 배포 기록 `/moimyeon/dev/deployments/2735d7d46fde` 생성, `/v1/terms` PRIVACY v1.2 확인 |

## 검증

- `infra/terraform/tests/deploy-source-verification-test.sh`: 새 경계 테스트 2건 추가. 수정 전 스크립트로 돌리면 "마감이 지나도 첫 확인은 해야 한다"로 실패, 수정 후 통과.
- `deploy-workflow-contract.sh` 통과.
- Terraform plan: 자원 구성·입력은 바뀌지 않는다. 다만 `infra/terraform/scripts`가 Terraform 경로 범위라 머지 후 Terraform Apply(변경 없는 동기화)가 돈다.
