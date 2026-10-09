# MOI-592 결정 기록

## D-1: 런타임 변경 판정은 한 스크립트, 목록에 없는 경로는 배포

- `.github/scripts/runtime-changes.sh <dev|live>`가 CI 후보 이미지, dev 배포·최신성, live 승격·후속 main 확인, live가 찾는
  dev 배포 기록 탐색을 모두 판정한다.
- 제외(배포 안 함): 문서, 하네스(`.agents` `.claude` `.codex` `.githooks`), `.worklog`, 저장소 도구 설정, 배포에 쓰이지 않는
  워크플로(review-swarm, linear-issue-for-pr, api-docs-pages, terraform-plan*), 이름으로 지정한 CI 보조 스크립트(새 스크립트는 지정 전까지 배포),
  테스트 코드(`*/src/test`, `tests/` — 실행용 구성이 `:tests:`를 쓰지 않는다는 검사로 전제를 고정),
  Terraform 계약 테스트, 다른 환경의 `envs/<env>`, `infra/observability`.
- 나머지는 배포한다. 빌드·배포·승격에 쓰이는 워크플로와 스크립트(ci, deploy-aws, promote-live, `infra/terraform/scripts`, 이 규칙 자신)는
  배포로 검증되도록 런타임 변경으로 둔다(이슈의 주의 사항).
- 근거: 최근 dev 60건 중 18건이 앱 코드 없는 커밋이었다(MOI-584 실측).

## D-2: Terraform 변경은 환경 단위로만 거른다

- 이슈의 [결정 필요] "Terraform 변경 커밋에서 앱 교체가 언제 필요한지"에 대한 답: 다른 환경의 값(`envs/live`↔`envs/dev`)만 빼고,
  공용 모듈·shared·자기 환경 변경은 배포한다.
- 이유: ECS 서비스는 `ignore_changes = [task_definition]`이라 Terraform이 바꾼 태스크 정의 원본 틀은 다음 배포가 등록해야 반영된다.
  "태스크 정의가 실제로 바뀌었는가"를 경로로는 알 수 없고, 적용 뒤 원본 틀 비교로 판단하는 것은 배포 기록 정합(MOI-589) 이후 과제로 둔다.
  PR #175(`live.tfvars`만 변경)처럼 다른 환경 값만 바뀐 경우가 확실한 낭비라 그것부터 뺀다.

## D-3: live가 찾는 dev 배포 기록은 dev 규칙으로 비교한다

- promote-live는 main source와 "런타임이 같은" dev revision 중 배포 기록이 있는 것을 찾는다. dev가 어떤 커밋을 배포했는지는 dev 규칙이
  정했으므로 같은 dev 규칙으로 비교해야 한다. live 규칙을 쓰면 `envs/live`만 바뀐 커밋을 "다르다"로 보고 기록을 못 찾는다.
- main 승격 여부·main 전용 변경·후속 main 확인은 live 규칙을 쓴다(`envs/live` 변경은 원본 틀 반영을 위해 승격).

## D-4: 승격은 규칙 스크립트를 workflow revision에서 읽는다

- promote-live는 기본 브랜치의 workflow 파일과 main의 체크아웃을 함께 쓴다. main에 아직 스크립트가 없는 릴리스를 승격하면 실패한다.
  `git show ${{ github.workflow_sha }}:.github/scripts/runtime-changes.sh`로 workflow와 같은 revision의 규칙을 쓴다(기본 브랜치 코드라 신뢰 범위 동일).

## D-5: `infra/observability`는 Terraform 입력이다

- `modules/moimyeon-environment/monitoring.tf`가 이 디렉터리를 읽는데, Terraform Apply 생략 판단과 배포의 경계 대기는 `infra/terraform`만 봤다.
  모니터링 설정만 바꾼 커밋은 다음 Terraform 변경까지 dev에 적용되지 않았다. 두 곳 모두에 추가하고 계약 테스트로 같게 묶는다.
  Terraform이 읽지 않는 `infra/observability/tests`·`README.md`는 뺀다(불필요한 경계 대기 방지).
- 첫 적용 주의: 아직 적용되지 않은 모니터링 설정이 있으면 `user_data_replace_on_change`로 dev 모니터링 EC2가 교체될 수 있다(데이터 EBS는 별도).
  머지 후 Terraform Apply 요약에서 `aws_instance.monitoring` 교체 여부를 확인한다.
- `infra/terraform/scripts` 변경도 경계 대기를 만든다(PR #186). Terraform 설정은 이 스크립트를 쓰지 않지만 Apply workflow가 일부를 실행하므로
  이번에는 범위를 바꾸지 않는다.

## 리뷰 반영(qa-reviewer PASS, 권고 6건 모두 반영)

- dev 기록 탐색의 git diff를 변수에 먼저 받아 실패가 "같은 런타임"으로 읽히지 않게 함
- `.github/scripts`는 와일드카드 대신 파일 이름 목록
- 실행용 구성의 `:tests:` 의존 금지 검사, promote-live의 규칙 호출 횟수(live 3·dev 1) 검사, workflow SHA 형식 검사
- `infra/observability/tests`·`README.md`를 Terraform 입력에서 제외
