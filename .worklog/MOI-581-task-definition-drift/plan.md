# MOI-581 첫 PR 계획 — ECS task definition 영구 diff 제거

## 승인 체크포인트

- [x] 변경 계획 승인 (2026-10-07)
- [ ] Terraform plan 승인 (머지 = dev apply 승인)
- [x] 커밋·PR 승인 (2026-10-07)

체크박스는 사람이 승인한 뒤에만 표시한다.

## 목표

dev에 적용한 직후의 plan이 "변경 없음"으로 수렴하게 한다. 같은 모듈을 쓰는 live도 첫 적용부터 같은 문제가 없게 한다.

## 변경 접근

1. ECS가 채우는 기본값을 컨테이너 정의에 명시한다. 실제 동작 값은 바꾸지 않는다.
   - log-router: `user = "0"`, `portMappings = []`, `systemControls = []`, `volumesFrom = []`
   - 앱 컨테이너(core-api·core-worker): `mountPoints = []`, `systemControls = []`, `volumesFrom = []`,
     `portMappings`가 없는 Worker는 `portMappings = []`
   - 로그 버퍼 볼륨: `configure_at_launch = false`
2. 로그 라우팅이 꺼진 환경(live 기본값 `disabled`)에서도 같은 기본값을 적어 컨테이너 정의가 수렴하게 한다.
3. 회귀 방지: 기존 Terraform 모듈 테스트(`logging.tftest.hcl`)에 위 기본값이 정의에 들어 있는지 확인하는 검사를 더한다.

## 검증 계획

- `terraform fmt -check`, shared/dev/live `terraform validate` (CI와 같은 1.15.9, Docker)
- 모듈 테스트 `terraform test` (mock provider, AWS 접근 없음)
- PR CI의 dev plan 판독: 기대 결과는 task definition 교체·배포 설정 SSM·IAM 정책 갱신이 모두 사라짐.
  다른 자원 변경이 보이면 정지하고 보고한다.
- 머지 후: 다음 drift plan 성공(변경 없음) 확인

## 위험과 복구

- 기대와 달리 plan에 교체가 남으면, 적용해도 지금과 같은 상태(이미 매번 적용되던 교체)이므로 악화는 없다.
  남은 속성을 로그로 확인해 다시 맞춘다.
- 실행 중인 ECS 서비스는 task definition 변경을 무시하므로(`ignore_changes`) 이 변경으로 재시작되지 않는다.

## 영향 범위

- 환경: dev(머지 후 자동 적용), live(아직 미생성, 첫 생성부터 반영)
- 외부 소비자·API: 영향 없음
- 시크릿: 다루지 않음
