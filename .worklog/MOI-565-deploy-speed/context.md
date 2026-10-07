# MOI-565 배포 속도 개선 — 컨텍스트

## 이슈

- 배경: dev 배포 1회에 약 30분이 걸린다.
- 목표: 5분 이하, 가능한 한 짧게.
- 하위 이슈·커멘트·관련 Wiki 없음. 인프라·워크플로 작업이라 Wiki 비대상.

## 측정 (2026-10-05~06 dev 배포 10회)

`Deploy AWS` 22~25분 + dev push `CI` 5~7분 ≈ 30분. 최근 실행 기준:

| 구간 | 시간 | 내용 |
| --- | --- | --- |
| dev push CI | 7.2분 | PR에서 이미 같은 트리로 통과한 검사를 다시 수행 |
| `candidate` | 9.3분 | 같은 SHA의 `Terraform Apply` 완료 대기 |
| ┗ Terraform plan/apply | 약 2.5분 | 인프라 변경이 없어도 매번 no-op plan 수행 |
| ┗ `sync-dev-variables` | 6.5분 | `terraform output -raw`를 값마다 호출하고, 호출마다 `terraform init` |
| `deploy` 이미지 빌드·푸시 | 4.7분 | Docker 안 Gradle 컴파일 3분(캐시 없음), `cache-to mode=max` 내보내기 약 80초 |
| `deploy` API ECS 교체 | 5.9분 | grace 240초, ALB healthy 2회 × 30초, Worker 이미지 조립 병렬 |
| `deploy` Worker ECS 교체 | 2.3~2.8분 | API 안정화 후 순차(DR-003) |

### ECS 교체 세부 (run 37423665912 로그)

- API(5분 51초): 새 태스크가 `pending=1`로 약 4분 대기 → 실행 후 ALB 정상 판정 약 50초 →
  기존 태스크 정리·완료 판정 약 65초. 4분 대기는 여유 용량 없는 EC2(`target_capacity = 100`,
  t3.small)에서 새 인스턴스를 기다린 것으로 추정(ECS 이벤트 미확인, AWS 자격증명 없음).
- Worker(2분 48초): 기존 태스크 중지 → `pending` 약 50초 → 실행 후 완료 판정까지 약 80초.

## 저장소 설정 (확인함)

- dev ruleset `dev-protection`: PR 필수, required check `build`, `strict_required_status_checks_policy: true`
  (머지 전 branch update 의무), bypass actor 없음, non-fast-forward 금지.
- 허용 머지 방식: merge·squash. strict 정책이면 머지 결과 트리 = CI가 검증한 PR head 트리.

## 관련 코드

- `.github/workflows/deploy-aws.yml` — dev 배포. `candidate`(68행), ECS 교체(348행)
- `.github/workflows/terraform-apply.yml` — CI `workflow_run`마다 shared/dev plan·apply·변수 동기화
- `.github/workflows/terraform-sync-variables.yml` — 변수 동기화·dev 배포 설정 artifact 발행
- `infra/terraform/scripts/terraform-command.sh:131` — `output-raw`마다 `init` 수행
- `infra/terraform/scripts/sync-github-variables.sh`, `deploy_config.py` — 출력값 개별 조회
- `infra/terraform/scripts/wait-for-terraform-apply.sh` — 15초 폴링 대기
- `Dockerfile` — builder 단계에서 `bootJar` 2개
- `infra/terraform/modules/moimyeon-environment/{ecs,alb_dns,worker_ecs}.tf`, `envs/dev/main.tf`

## 기준 결정 (변경 시 충돌 대상)

- `docs/knowledge/infra.md` "CI 성공 없이 배포 없음 — push에 독립 발화하는 배포 워크플로 금지"
- `.worklog/MOI-432/decisions.md` DR-001(dev 배포는 CI `workflow_run`으로만), DR-013(CI 성공마다
  no-op plan까지 수행하는 누락 방지, deploy는 같은 SHA Terraform Apply 성공 확인 후 진행)

## 경계

- live 승격·롤백 경로의 검증(계보·digest·marker)은 약화하지 않는다.
- `terraform apply`·콘솔 변경은 하지 않는다.
