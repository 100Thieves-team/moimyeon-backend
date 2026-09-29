# 개인 계정용 Organizations 구성 준비

## 배경과 결정

현재 팀 계정의 Terraform은 `shared/dev/live` 환경과 배포 파이프라인에 연결되어 있다. 향후 개인 계정에서 Organizations와 SCP를 도입할 때 팀 계정의 state·설정을 재사용하지 않도록 `infra/terraform/personal-organization`을 독립 루트로 만들었다. 계정과 권한 구조가 확정되기 전까지 OU는 비워 두고 SCP 연결 기본값은 `false`다.

## 실제 흐름

1. Terraform은 별도 S3 backend와 `management_account_id`로 제한한 개인 관리 계정의 자격 증명을 사용하도록 준비한다. 현재는 예시 파일만 있으며 backend bucket과 AWS 계정은 생성하지 않았다.
2. 향후 별도 plan을 확인한 뒤 Organization, `dev/live` OU, IAM User·Access Key 제한 SCP, 리전 제한 SCP를 만들 수 있다. 기존 Organization이 있으면 새로 만들지 않고 import하고, 기존 정책 유형과 trusted access 서비스 목록을 입력에 반영한다.
3. 멤버 계정이 준비되고 SCP 영향이 검증되면 별도 변경으로 `attach_guardrails`를 켜고 계정을 OU에 배치한다. 관리 계정에는 SCP가 적용되지 않는다.

## 전후 결과와 배포 영향

- 변경 전: 개인 계정용 Organizations 설정 원본이 없었다.
- 변경 후: 독립 루트와 목표 구조 설명이 생겼다. 현재 AWS 자원과 팀 계정의 Terraform state는 변경되지 않았다.
- 기존 CI의 Terraform plan/apply는 `envs/shared`, `envs/dev`, `envs/live`만 대상으로 한다. PR Terraform Plan 워크플로는 `infra/terraform/**` 변경으로 실행되지만 개인 루트의 자원을 plan하지 않는다.
- `dev`에 머지하면 현재 `deploy-aws.yml`은 새 `.tf` 파일을 앱 배포 대상 변경으로 판단한다. 따라서 기존 앱의 재배포가 발생할 수 있으며, 이를 무영향 배포라고 보증할 수 없다.
- `main` 머지에서도 live 승격 게이트가 켜져 있다면 동일한 배포 분류가 적용된다.

## 검증과 한계

개인 루트의 `terraform fmt -check`, `terraform validate`, 기존 Terraform 설정 계약 검사와 `./gradlew test ktlintCheck`가 통과했다. 개인 계정 자격 증명과 backend가 없어 해당 계정의 실제 Terraform plan은 아직 만들 수 없다. [구조와 운영 절차](../../infra/terraform/personal-organization/README.md)에 적용 전 확인 사항을 기록했다.
