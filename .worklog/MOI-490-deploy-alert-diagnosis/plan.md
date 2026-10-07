# MOI-490 1번 PR 계획 — dev 배포·Terraform 적용 알림 보완

## 승인 체크포인트

- [x] 변경 계획 승인 (2026-10-07: 결정 1번 제안대로)
- [x] 대상별 검증 결과 승인 (2026-10-07; Actions·셸만 변경, Terraform 구성 불변이라 plan 비대상)
- [x] 커밋·PR 승인 (2026-10-07)

체크박스는 사람이 승인한 뒤에만 표시한다.

## 범위

MOI-490 인수 조건 중 저장소 안에서 끝나는 부분만 다룬다. n8n 전송·중복 제거·이력 저장, AI 진단, PR 위험 요약은
후속 PR이다. live 알림 동작(promote-live·rollback·main 브랜치 Terraform)은 바꾸지 않는다(MOI-512).

## 변경 접근

1. 알림 스크립트
   - webhook 미설정: 지금처럼 배포를 실패시키지 않는다(계약 불변식). 대신 Actions 경고 표시와 실행 요약에
     "알림 미전송(webhook 미설정)"을 남겨 조용히 넘어가지 않게 한다.
   - 전송 실패: 경고 표시와 실행 요약을 남긴다. 알림 스텝 격리(`continue-on-error`)는 유지해 배포 결과를 덮지 않는다.
   - 메시지에 결과 단계(어디서 멈췄는지)와 이미지 출처(PR 이미지 승격/배포 중 빌드) 항목을 받을 수 있게 한다.
     값이 없으면 기존 메시지와 같게 보여 live 경로 호환을 유지한다.
2. dev 배포 workflow
   - 준비 단계 실패를 원인별로 구별한다: PR CI 검증 실패 / Terraform 적용 대기 시간 초과.
   - 준비 단계 취소도 `cancelled`로 알린다.
   - 더 새 커밋에 밀려 건너뛴 배포를 `skipped`로 알린다(실패 아님).
   - Worker 이미지 빌드 실패면 Worker 결과를 `skipped`가 아니라 빌드 실패로 표시한다.
   - 배포 중 이미지를 빌드한 경우 메시지에 표시한다.
   - 문서 전용 미배포는 지금처럼 Slack을 보내지 않는다.
3. Terraform Apply workflow (dev 브랜치만)
   - 모든 job 뒤에 `always()` 알림 job을 두고 shared/dev plan·apply·변수 동기화 결과를 dev webhook으로 보낸다.
   - 결과를 성공(적용함) / 변경 없음 / 실패(어느 단계) / 취소로 구별한다.
   - 인프라 무변경으로 Terraform을 생략한 실행과, 더 새 커밋 때문에 적용하지 않은 실행은 Slack을 보내지 않고
     실행 요약만 남긴다(앱 커밋마다 생기는 정상 경로라 소음이 된다).
   - main 브랜치(live) Terraform 알림은 MOI-512로 남긴다.
4. 계약 검사·테스트
   - 위 동작을 `release-workflow-contract.sh`·`terraform-ci-contract.sh`에 고정한다. 기존 검사는 약화하지 않는다.
   - 알림 스크립트의 payload·미설정·전송 실패 동작을 셸 테스트로 검증한다(가짜 webhook 서버 또는 curl 대체).

## 결정

1. 알림을 보낼 범위
   - 결정(2026-10-07): 더 새 커밋에 밀려 건너뛴 배포는 Slack으로 짧게 한 줄 보낸다(머지한 사람이 자기 커밋이 왜
     따로 배포되지 않았는지 알 수 있게). 문서 전용 미배포와 Terraform 생략은 Slack 없이 실행 요약에만 남긴다.

## 리뷰 반영 (2026-10-07, qa-reviewer·code-reviewer)

- 필수: plan/apply 공통 workflow는 밀린 실행을 `current=false` + job 실패로 끝낸다. 실패 판정보다 먼저 밀림을 걸러야
  Slack에 실패로 가지 않는다. apply 공통 workflow에 `current` 출력을 추가했다.
- workflow 안의 판정 로직을 `notify-dev-deployment.sh`·`summarize-terraform-result.sh`로 옮기고 경우별 테스트로 고정했다.
- shared를 적용한 뒤 dev가 밀리면 새 실행은 shared를 변경 없음으로 보므로, 이 실행이 shared 적용을 알린다
  (계획 3번 "밀린 실행은 Slack 없음"의 예외).
- Core API 빌드 실패·Worker 이미지 재사용을 이미지 출처에 정확히 표시하고, PR CI 검증 실패 문구가 원인을 단정하지
  않게 했다.
- `needs:.*apply-shared` 계약 검사가 새 job 때문에 느슨해지지 않도록 plan-dev 블록만 보게 했다.

## 리뷰 반영 (2026-10-07, Codex gpt-6-astra)

- 필수: 변수 동기화 workflow도 밀린 실행을 `current=false` + job 실패로 끝낸다. `current` 출력을 추가하고 밀림 판정에
  포함했다. 이미 적용한 환경이 있으면 그 적용을 알린다.
- 필수: 알림 job의 checkout·판정 단계 실패가 Terraform Apply 실행을 실패로 만들지 않도록 job 수준 `continue-on-error`를 뒀다.
- 변수 동기화는 배포용 적용 기록(SSM)을 먼저 남기므로, 동기화 실패 문구가 배포 지연을 단정하지 않게 했다.

## 리뷰 반영 (2026-10-07, ship-pr qa-reviewer 게이트: PASS, 권고 반영)

- job 사이 취소로 필요한 단계가 skipped로 남으면 "변경 없음"이 아니라 cancelled(멈춘 단계)로 알린다.
- skipped 한 줄 축약은 dev 배포에만 쓴다(live rollback의 skipped는 기존 메시지 유지).
- 준비 단계 취소도 멈춘 단계를 표시한다.

## 검증 계획

- `bash infra/terraform/tests/release-workflow-contract.sh`
- `bash infra/terraform/tests/deploy-workflow-contract.sh`
- `bash infra/terraform/tests/terraform-ci-contract.sh`
- 알림 스크립트 셸 테스트(신규)
- 모든 변경 셸 스크립트 `bash -n`, actionlint
- Terraform 구성 불변 → plan 비대상(사유 기록)
- dev 채널 실제 수신 확인은 머지 후 첫 배포에서 사람이 확인

## 영향 범위

- 환경: GitHub Actions의 dev 배포·dev Terraform Apply 경로. 공용 알림 스크립트는 live 경로도 쓰므로 기존 입력만 줄 때
  메시지가 같아야 한다
- Terraform 자원: 변경 없음. 다만 `infra/terraform/**` 경로를 바꾸므로 PR Terraform plan이 돈다(변경 없음이어야 한다).
  머지 뒤 dev Terraform Apply도 실행되어 첫 Terraform 알림은 "No infrastructure changes to apply"가 되고, 그 머지의
  배포는 이 Terraform 적용을 기다린다
- 시크릿: 기존 `SLACK_DEPLOY_WEBHOOK_URL_DEV`만 참조, 값은 다루지 않는다
- API 문서·프론트 계약: 영향 없음
