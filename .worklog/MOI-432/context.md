# MOI-432 컨텍스트

## 이슈 요약

[MOI-432 인프라 및 배포 개선](https://linear.app/100-thieves/issue/MOI-432/인프라-및-배포-개선)은
Core API와 Worker를 순차 빌드·배포해 약 9분 25초가 걸리는 현재 파이프라인을 줄이고,
CI 게이트·build once promote·롤백·live 승인·알림·스모크 테스트·Terraform CI를 갖추는
상위 인프라 이슈다. Linear에는 하위 이슈·댓글·첨부 문서가 없고, 프로젝트
[인프라 구축](https://linear.app/100-thieves/project/인프라-구축-0b711c220a8c)에도 연결 문서가 없다.
팀 Notion 검색에서도 이 프로젝트의 요구사항 근거로 볼 수 있는 문서를 찾지 못했다.

## 요구사항 핵심

- CI가 성공한 커밋만 배포한다.
- dev에서는 Core API 이미지 빌드 후 API 배포와 Worker 이미지 빌드를 병렬로 진행하고,
  API가 안정화된 뒤에만 Worker를 배포한다.
- 한 Docker 빌드 그래프에서 Core API와 Worker bootJar를 만들고 런타임 target만 나눈다.
- 문서만 바뀐 커밋은 배포하지 않는다.
- live는 dev에서 검증된 이미지를 재빌드하지 않고 승격하며 승인 게이트를 거친다.
- 배포 실패·롤백 알림과 배포 후 스모크 테스트가 존재해야 한다.
- Terraform은 PR plan과 승인된 CI apply로 운영하고 시크릿 원본은 SSM SecureString으로 단일화한다.

작업 세션의 `infra-change` 기준과 이슈에 승인된 파이프라인 원칙을 불변식으로 적용하며,
현재 코드와 충돌할 때 이 작업에서 새로 만드는 경로는 반드시 이를 만족해야 한다.

## 관련 코드

- `.github/workflows/ci.yml`: dev/main push와 PR에서 테스트·lint를 실행한다.
- `.github/workflows/deploy-aws.yml`: 현재 dev/main push에 독립 발화하며 API 빌드·안정화 뒤 Worker를 빌드한다.
- `Dockerfile`: Gradle task 인자로 API와 Worker 중 하나만 빌드하는 단일 runtime target 구조다.
- `infra/terraform/README.md`: 기존 dev/main push 배포 운영 절차를 설명한다.
- `infra/terraform/modules/moimyeon-environment/iam.tf`: GitHub Actions OIDC 배포 권한과 브랜치 신뢰를 정의한다.
- `infra/terraform/scripts/resolve-deploy-candidate.sh`: 이번 변경에서 추가한 최신 CI 성공 revision 선택 경계다.
- `infra/terraform/scripts/sync-github-variables.sh`: Terraform output을 GitHub variables로 동기화한다.
- `infra/terraform/tests/ecs-capacity-ownership.sh`: CI에서 실행하는 인프라 정적 계약 테스트의 선례다.

## 작업 경계

이번 첫 슬라이스는 dev 자동 배포의 CI 게이트, 문서 전용 변경 제외, Docker multi-target,
API 안정화와 Worker 빌드 병렬화, 배포 순서 단조성 보장까지다. live 자동 배포는 build once promote를 위반하므로
제거하지만, 승격·승인·롤백·알림·스모크·Terraform CI는 결정이 필요한 후속 슬라이스로 남긴다.
Terraform 리소스와 운영 환경은 변경하거나 apply하지 않는다.
