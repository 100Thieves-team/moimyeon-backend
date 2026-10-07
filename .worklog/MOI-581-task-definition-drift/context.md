# MOI-581 컨텍스트 — ECS task definition 영구 diff 제거

## 이슈 요약

[MOI-581](https://linear.app/100-thieves/issue/MOI-581)은 live 인프라 구성·활성화 이슈다. 이 브랜치는 그 선행 작업인
"dev에서 관찰된 반복 변경의 원인 확인과 live 첫 적용 전 해소"만 다룬다. live 인프라 생성·활성화는 후속 PR이다.

## 관찰

- 매일 도는 dev drift plan이 2026-09-16부터 계속 실패한다.
  - 09-16~19: launch template 1건(새 ECS 최적화 AMI). 이후 사라짐.
  - 09-20부터: `aws_ecs_task_definition.app`, `notification_worker` 교체가 매일 반복된다. FireLens log-router가
    추가된 커밋(0eb1f6dd, 09-20)과 시작 시점이 같다.
- MOI-565(#159)에서 배포 설정 SSM 파라미터가 task definition ARN을 담게 되면서, 교체될 때마다 그 파라미터와
  배포 role IAM 정책도 함께 갱신 대상이 된다. dev 적용이 성공한 직후에도 다음 plan에 같은 5건이 다시 나온다.
- 10-07 drift 로그의 속성 차이(값은 생략):
  - 앱 컨테이너(core-api·core-worker): `mountPoints = []`, `systemControls = []`, `volumesFrom = []`,
    Worker는 `portMappings = []`도
  - log-router: `portMappings = []`, `systemControls = []`, `user = "0"`, `volumesFrom = []`
  - 로그 버퍼 볼륨: `configure_at_launch = false`(교체에 따라 다시 만들어지는 표시)
  - 이미지·환경변수·자원량 등 실제 값의 차이는 없다.

## 원인 판단

ECS가 등록 시 채우는 기본값을 코드가 적지 않아 provider가 정의를 다르다고 판정한다. 특히 FireLens가 log-router에
자동으로 넣는 `user = "0"`은 provider의 기본값 무시 대상이 아니어서, log-router 추가 뒤부터 교체가 시작된 것으로
보인다. 확정은 수정 후 PR dev plan으로 한다.

## 관련 코드

- `infra/terraform/modules/application-logging/outputs.tf`: log-router 컨테이너 정의(`routers`)
- `infra/terraform/modules/moimyeon-environment/application_logging.tf`: 로그 볼륨·앱 컨테이너 공통 설정
- `infra/terraform/modules/moimyeon-environment/ecs.tf`: Core API 컨테이너·task definition
- `infra/terraform/modules/moimyeon-environment/worker_ecs.tf`: Worker 컨테이너·task definition
- `infra/terraform/modules/*/tests/logging.tftest.hcl`: 로그 라우팅 계약 테스트
- `infra/terraform/modules/moimyeon-environment/deploy_candidates.tf`: 배포 설정 SSM(task definition ARN 포함)

## 작업 경계

- 컨테이너의 실제 동작 값(이미지·환경변수·자원량·로그 설정)은 바꾸지 않는다.
- `terraform apply`, 콘솔 변경은 하지 않는다. 적용은 머지 후 CI가 한다.
- live 인프라 생성·활성화, live 설정값 결정은 이 PR 범위가 아니다.
