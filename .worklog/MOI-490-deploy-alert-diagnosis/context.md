# MOI-490 컨텍스트

## 이슈 요약

[MOI-490](https://linear.app/100-thieves/issue/MOI-490)은 dev 배포 결과를 세분해 알리고, AI 실패 진단·PR 위험
요약·커밋에서 배포 결과까지의 이력 연결을 제공하는 DevOps 작업이다. 2026-10-07에 MOI-565(dev push 즉시 배포·PR
이미지 승격) 이후 흐름에 맞춰 이슈 본문을 갱신했다. 제품 명세가 아닌 인프라 작업이라 Wiki는 조회하지 않았다.
live 확장은 MOI-512·MOI-513이 이어받는다.

## 작업 분할 (2026-10-07 합의)

1. 저장소 안에서 끝나는 알림 보완: webhook 미설정 식별, 취소·건너뜀·실패 원인 구별, Terraform 적용 알림 ← 이 브랜치
2. n8n 이벤트 전송과 중복 제거·이력 저장
3. Hermes 실패 진단
4. PR 위험 요약

## 현재 배포 흐름 (dev 기준)

- Deploy AWS: dev push로 시작 → 배포 대상 판정(문서 전용이면 미진행) → 준비(PR CI 트리 검증, Terraform 적용 경계
  대기 최대 90분, lock 밖) → 배포(lock·대기열, 더 새 커밋이면 건너뜀, PR 이미지 승격 또는 배포 중 빌드, API 후 Worker)
- Terraform Apply: dev push CI 성공 후 시작, 마지막 적용 이후 인프라 무변경이면 생략, 적용 SHA를 SSM에 기록
- 이미지 출처: PR CI의 `tree-<트리>-run-<실행>-<시도>` 후보 태그. 배포 실행과 이미지를 만든 실행이 다르다

## 1번 PR 관점의 현재 공백

- 준비 단계 실패 알림은 PR CI 검증 실패와 Terraform 대기 실패를 같은 문구로 보낸다
- 준비 단계 취소, 더 새 커밋에 밀린 건너뜀은 알림이 없다
- Terraform Apply workflow에는 Slack 알림이 없다
- webhook 미설정이면 알림 스크립트가 성공 종료로 조용히 넘어가고, 전송 실패는 로그에만 남는다
- Worker 이미지 빌드 실패 시 Slack에는 Worker가 `skipped`로만 표시된다

## 관련 코드

- `.github/workflows/deploy-aws.yml`: dev 배포, 준비 실패 알림 job(`prepare-failed`), 배포 job 마지막 Slack 스텝
- `.github/workflows/terraform-apply.yml`: merge 후 Terraform plan·apply·변수 동기화 orchestration (알림 없음)
- `.github/workflows/promote-live.yml`, `.github/workflows/rollback-aws.yml`: 같은 알림 스크립트를 쓰는 live 경로
- `infra/terraform/scripts/notify-deployment.sh`: Incoming Webhook payload와 전송
- `infra/terraform/scripts/verify-pr-ci.sh`, `wait-for-terraform-boundary.sh`: 준비 단계 실패 원인
- `infra/terraform/tests/deploy-workflow-contract.sh`, `release-workflow-contract.sh`, `terraform-ci-contract.sh`:
  workflow 불변식 계약 검사
- `docs/knowledge/infra.md`: MOI-565 배포 불변식

## 이전 작업물

`.worktrees/moi-490-deployment-alert`(브랜치 `feat/MOI-490-deployment-alert`)에 커밋되지 않은 Terraform plan 위험
평가 eval·스크립트와 worklog가 있다(2026-08-31, 옛 이슈 범위 기준). 4번 PR(PR 위험 요약)에서 재사용 여부를 정한다.

## 작업 경계

- live 알림 동작을 바꾸지 않는다(MOI-512). 공용 알림 스크립트를 바꾸면 live 경로 호환을 유지한다
- Slack·GitHub secret 값 설정, webhook·채널 생성, `terraform apply`, 운영 리소스 수동 변경은 하지 않는다
- 배포 속도·이미지 승격 방식 자체는 바꾸지 않는다(MOI-565)
