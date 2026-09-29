# 컨텍스트

[MOI-554](https://linear.app/100-thieves/issue/MOI-554/개인-aws-계정-이관을-위한-organizationsscp-terraform-기반-준비) 작업이다.

사용자는 [멘토링 노트](https://app.notion.com/p/3ea5a70afcf680699587f4e1d618d5c9)에서 AWS 계정 분리, Organizations/OU, SCP, IAM Identity Center를 학습했다. 현재 팀 AWS 계정에서는 이 조직 단위 기능을 사용할 수 없다. 추후 개인 AWS 계정으로 이관할 때 편하도록 Terraform 구성을 기존 환경과 별도 디렉터리에 준비하려 한다.

## 관련 코드

- `infra/terraform/envs/shared`: 현 팀 계정의 공통 자원, 배포 파이프라인과 연결됨.
- `infra/terraform/envs/dev`, `envs/live`: 현 팀 계정의 애플리케이션 환경.
- `infra/terraform/modules/shared-foundation`: 현 팀 계정의 공유 자원 모듈.
- `infra/terraform/README.md`: 기존 배포 구조와 계정/상태 운영 설명.

## 경계

- 현 팀 계정의 Terraform 루트나 배포 경로는 변경하지 않는다.
- 개인 AWS 계정에 대한 실제 자원 생성, 기존 계정 자원 이동, `terraform apply`는 수행하지 않는다.
- 개인 계정 식별자, 이메일, 자격 증명, 시크릿 값은 저장소에 기록하지 않는다.
