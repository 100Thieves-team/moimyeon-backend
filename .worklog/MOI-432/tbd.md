# MOI-432 TBD 선택지

선택지는 판단 근거 보존을 위해 남긴다. 모든 항목은 `decisions.md`로 옮겼다.

## 결정 현황

| 항목 | 선택 | 상태 |
| --- | --- | --- |
| 1. live 승격 트리거 | A. main 머지 자동 실행 | 확정 |
| 2. live 애플리케이션 배포 책임 | D. main에 머지한 사람이 책임 | 확정, PR reviewer·Environment reviewer 없음 |
| 3. 원클릭 롤백 | B. 개발 플랫폼 UI/ChatOps | 확정 |
| 4. 배포 알림 | A. Incoming Webhook | 확정 |
| 5. 스모크 테스트 | B. readiness + public DB read | 확정 |
| 6. 배포 컨트롤러 | C. ECS native blue/green | 확정: controller는 ECS 통일, Worker 전략은 rolling |
| 7.1 Terraform CI | A. PR plan + merged exact-plan 무승인 자동 apply | 확정 |
| 7.2 설정·시크릿 원본 | 비민감 tfvars는 Git, 앱 시크릿은 사전 생성 SSM, 신규 live RDS는 Secrets Manager | 확정 |

공통 불변식:

- CI 성공 없이 배포하지 않는다.
- live는 dev에서 검증된 이미지를 재빌드하지 않고 승격한다.
- live 애플리케이션 배포는 CI를 통과한 변경을 main에 머지한 사람이 책임진다.
- 롤백은 개발 플랫폼 권한·감사 경계를, Terraform apply는 별도 확인 경계를 지난다.
- 롤백은 이미 존재하는 불변 이미지로 수행하며 재빌드하지 않는다.
- 시크릿 값은 로그·코드·plan·state에 새로 남기지 않는다.
- 공식 환경 plan은 committed `{env}.tfvars`만 명시적으로 사용하고 local override를 읽지 않는다.
- 로컬·에이전트가 Terraform apply를 실행하지 않는다.

## 1. live 승격 트리거

상태: **A로 확정**. main 머지가 자동으로 live 승격을 실행한다.

결정할 것: main 머지 뒤 live 배포 요청을 자동 생성할지, 운영자가 수동 실행할지.

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| A. main 머지 시 자동 승격 | 릴리스 누락이 없고 main 이력과 배포 감사 로그가 정렬된다. main 머지 행위가 배포 의도를 명확히 남긴다. | main이 dev에서 온 커밋인지, 대응하는 dev image digest가 실제 존재하는지 fail-closed 검증이 필요하다. 잘못된 main 머지는 곧바로 live 배포로 이어질 수 있다. |
| B. 수동 `workflow_dispatch` + dev SHA 입력 | 초기 구현이 단순하고 운영자가 릴리스 시점을 명시적으로 고른다. 저빈도 live 릴리스에 적합하다. | SHA 오입력·실행 누락 가능성이 있고, main과 live 상태가 쉽게 어긋난다. 입력 검증과 별도 감사 설명이 필요하다. |
| C. release tag 생성 시 승격 | 릴리스 의도가 불변 tag로 남고 changelog와 연결하기 쉽다. | 잘못된 tag 생성도 배포 요청이 된다. tag 보호·삭제 정책과 main/dev 계보 검증이 추가로 필요하다. |

### 추천

**A. main 머지 시 자동으로 live 승격을 실행**한다. 별도 reviewer 승인은 두지 않는다.

- main merge commit에서 dev 쪽 부모를 찾아 그 SHA의 API·Worker immutable image가 dev ECR과
  `last deployed` SSM에 존재하는지 검증한다.
- dev와 live ECR repository가 분리돼 있으므로 source digest를 live repository로 **복사**하고,
  복사 전후 manifest digest가 같은지 확인한다. 빌드는 다시 실행하지 않는다.
- source image가 없거나 dev 배포 성공 기록과 맞지 않으면 live 권한을 얻기 전에 실패한다.
- 수동 dispatch는 정상 릴리스가 아니라 자동 실행 재시도와 긴급 운영용으로만 남긴다.

뒤집히는 조건: main이 dev 병합 이외의 경로로 자주 갱신되거나 live 릴리스가 월 1회 이하라 자동 요청의
잡음이 더 크면, B를 임시 기본으로 사용하고 source SHA를 필수 검증한다.

## 2. live 승인 게이트

상태: **D로 확정**. PR 승인과 live Environment reviewer를 요구하지 않는다.

- CI를 통과한 변경을 main에 머지한 사람이 live 애플리케이션 배포를 결정하고 결과를 책임진다.
- PR은 변경 이력과 CI 연결을 위해 유지하되 required approving reviews는 0으로 둔다.
- `live-app` Environment는 branch·OIDC·변수 경계로 사용할 수 있지만 required reviewers는 두지 않는다.
- 긴급 롤백은 개발 플랫폼의 사용자 권한·확인 UI·감사 로그가 승인 경계다.
- Terraform apply는 7.1에서 정한 별도 `live-infra` 확인 경계를 유지한다.

결정할 것: 누가 어떤 조건으로 live 배포·롤백·Terraform apply를 승인할지.

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| A. 특정 개인 reviewer | 설정이 가장 단순하고 책임자가 명확하다. | 휴가·퇴사·장애 대응 때 단일 병목이 된다. 본인 실행·본인 승인 위험이 있다. |
| B. 배포 담당 팀 reviewer | 담당자 교대와 온콜 운영이 쉽고 개인 변경 없이 멤버십으로 권한을 관리한다. | GitHub required reviewer는 목록에서 **한 명의 승인**만 요구하므로 2인 동시 승인을 강제하지 못한다. 팀 멤버 관리가 필요하다. |
| C. PR 승인만 배포 승인으로 간주 | 기존 코드 리뷰 흐름을 재사용한다. | 코드 승인과 실제 배포 시점이 다르며, 오래된 승인으로 live 상태를 바꿀 수 있어 배포 게이트가 아니다. |
| D. main 머지 행위가 배포 결정 | 두 명이 각자 맡은 변경과 배포 결과를 끝까지 책임지며 대기 승인이 없다. | 독립적인 두 번째 확인이 없으므로 잘못된 merge의 blast radius가 곧바로 live에 닿는다. branch·CI·smoke·자동 롤백이 더 중요해진다. |

### 선택 반영

팀 규모와 개인 책임 원칙에 따라 **D. main에 머지한 사람이 live 애플리케이션 배포를 책임지는 방식**으로
확정했다. 상대방 PR 승인과 배포 직전 Environment 승인은 요구하지 않는다.

현재 저장소는 public이고 조직은 Free plan이므로 required reviewers 사용 조건을 충족하는 것을 확인했다.
근거: [GitHub Deployments and environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments).

## 3. 원클릭 롤백

상태: **B로 확정**. 팀 개발 플랫폼에서 배포 이력을 조회·선택한다.

- 브라우저는 AWS 자격 증명을 가지거나 ECS를 직접 변경하지 않는다.
- 플랫폼 백엔드가 사용자 권한과 선택한 deployment bundle을 검증한 뒤 GitHub rollback workflow를 호출한다.
- GitHub workflow가 immutable digest 검증, ECS 갱신, SSM commit, Slack 알림을 소유한다.

결정할 것: 롤백 대상을 어떻게 선택하고 API·Worker를 어떤 단위로 되돌릴지.

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| A. `workflow_dispatch`에 `{env}-{sha12}` 입력 | 지금의 immutable tag 규칙을 그대로 쓰며 구현이 작다. GitHub 실행 이력이 감사 로그가 된다. | 사람이 SHA를 잘못 넣을 수 있고 UI에서 후보를 동적으로 보여주지 못한다. |
| B. 배포 이력 선택 UI/ChatOps | 현재·이전 배포를 조회해 선택하므로 입력 오류가 줄고 운영 경험이 좋다. | GitHub dispatch input은 동적 목록을 제공하지 않는다. 별도 CLI·GitHub App·Slack bot과 권한 관리가 필요하다. |
| C. 직전 ECS task definition으로만 복귀 | 입력이 없고 가장 빠르다. | 한 단계 전만 가능하고 API·Worker revision이 엇갈릴 수 있다. task definition의 이미지가 보존됐다는 가정에 묶인다. |

### 선택 반영

**B. 팀 개발 플랫폼에 배포 이력 선택 UI를 둔다.**

- UI는 GitHub deployment/run과 ECR immutable digest를 조합한 deployment bundle을 보여준다.
- 플랫폼 백엔드는 사용자 권한·현재 환경·target bundle을 검증하고 rollback workflow를 호출한다.
- rollback workflow는 새 task definition만 등록한다. Docker build는 실행하지 않는다.
- API·Worker 안정화 뒤 각 SSM `last deployed` 값을 갱신한다.
- 기본은 같은 bundle의 API·Worker를 함께 복귀하고, 부분 롤백은 이유를 감사 로그에 남긴다.

개발 플랫폼 장애 시를 위한 break-glass 수단은 동적 UI가 아닌 검증된 bundle ID를 받는 수동 dispatch로 남긴다.

## 4. 배포 알림

상태: **A로 확정**. Slack Incoming Webhook을 사용한다.

결정할 것: Slack 전송 방식, 시크릿 위치, 실패가 배포 결과에 미칠 영향.

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| A. Slack Incoming Webhook | 구현과 권한이 작고 JSON 한 번으로 전송한다. 채널별 webhook으로 blast radius를 제한할 수 있다. | URL 자체가 시크릿이며 채널이 고정된다. 전송한 메시지를 삭제·수정할 수 없다. |
| B. Slack bot `chat.postMessage` | thread·메시지 갱신·다중 채널 등 확장이 쉽다. | bot token과 scope가 더 넓고 설치·회전·권한 감사 부담이 커진다. |
| C. SNS/AWS Chatbot 계열 | AWS 알람과 통합하기 쉽고 GitHub가 Slack 비밀값을 직접 쓰지 않아도 된다. | GitHub run URL·commit·승인자 같은 배포 문맥 조립이 번거롭고 AWS 리소스가 늘어난다. |

### 추천

**A. Incoming Webhook으로 시작**한다.

- dev/live 채널 또는 webhook을 분리하고, URL은 AWS 밖 시크릿이므로 GitHub secret에 둔다.
- 이름 예시: `SLACK_DEPLOY_WEBHOOK_URL_DEV`, `SLACK_DEPLOY_WEBHOOK_URL_LIVE`.
- 알림 필드: 환경, 성공/실패/롤백, API·Worker 결과, source SHA와 digest, 실행자·main 머지자,
  소요 시간, GitHub run 링크, 실패 시 진단 step 링크.
- 최종 알림 job은 `if: always()`로 실행하되 webhook 실패가 배포 성공을 실패로 바꾸지 않게 한다.
  대신 workflow warning을 남긴다.
- webhook 값은 로그나 worklog에 출력하지 않는다. 유출 시 즉시 폐기·재발급한다.

thread 갱신이나 승인 ChatOps가 실제 요구가 될 때만 B로 전환한다.
근거: [Slack Incoming Webhooks](https://api.slack.com/messaging/webhooks).

## 5. 스모크 테스트

상태: **B로 확정**. readiness와 `/v1/terms`를 blocking smoke로 사용한다.

결정할 것: 배포 성공을 어떤 endpoint와 결과로 판정할지.

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| A. `/actuator/health/readiness`만 호출 | 인증이 없고 DB readiness까지 확인한다. 현재 ALB 정책과 일치한다. | ALB가 이미 확인하는 신호와 겹치며 Controller·Service·응답 직렬화 회귀를 찾지 못한다. |
| B. readiness + public DB read API | 인프라 readiness와 실제 애플리케이션 read 경로를 함께 검증한다. 별도 계정이 필요 없다. | seed/reference 데이터와 API 계약이 바뀌면 smoke도 함께 갱신해야 한다. |
| C. 인증된 synthetic 사용자 여정 | 인증·인가와 핵심 사용자 흐름까지 가장 넓게 확인한다. | synthetic 계정·토큰 회전·개인정보·쓰기 부작용을 관리해야 하며 배포 critical path가 길어진다. |

### 추천

**B를 blocking smoke로 사용하고 C는 후속 non-blocking synthetic으로 분리**한다.

1. `GET /actuator/health/readiness`: HTTP 200, health status `UP`.
   - 현재 readiness는 `readinessState,db`를 포함하고 Redis는 제외한다.
2. `GET /v1/terms`: HTTP 200, 공통 성공 응답과 `data` 구조 확인.
   - 공개 Controller → Service → DB → JSON 직렬화 경계를 지난다.

각 호출은 요청당 5초 제한, 짧은 backoff를 둔 3회 이내 재시도로 제한한다. 전체 smoke budget은 60초를
넘기지 않는다. live 실패는 traffic 전환 전이면 전환 금지, 전환 후면 자동 롤백 조건으로 연결한다.
dev 실패는 배포 실패로 표시하되 이전 API revision의 상태를 보존한다.

`/health`는 프로세스 응답만 확인하므로 blocking 목록에 추가하지 않는다. 인증 synthetic은 읽기 전용
전용 계정·토큰 수명·정리 정책이 합의된 뒤 별도 workflow로 도입한다.

## 6. Core API 배포 컨트롤러

상태: **C로 확정**.

- API와 Worker 모두 deployment controller는 `ECS`로 통일한다.
- Core API는 native `BLUE_GREEN` 전략을 사용한다.
- Worker는 ALB·사용자 traffic 전환 경계가 없으므로 같은 ECS controller 안에서 `ROLLING` 전략을 유지한다.

결정할 것: rolling을 유지할지, native ECS blue/green 또는 CodeDeploy로 옮길지.

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| A. 현재 rolling + 수작업 polling | 이미 검증됐고 추가 ALB target group이 필요 없다. | workflow bash가 길고 AWS 상태 모델을 중복 구현한다. 빠른 traffic 전환/복귀가 어렵다. |
| B. rolling + `aws ecs wait services-stable` + 요청 revision 사후 검증 | 변경이 작고 수작업 polling을 대부분 제거한다. Worker에도 같은 방식을 쓸 수 있다. | waiter만으로는 요청 revision이 롤백됐는지 보장하지 않으므로, 완료 뒤 PRIMARY task definition 일치 검사가 필요하다. 롤백 속도는 rolling 수준이다. |
| C. Amazon ECS native `BLUE_GREEN` | green 검증, managed traffic shift, bake time, lifecycle hook, CloudWatch alarm rollback을 ECS 서비스 안에서 제공한다. CodeDeploy AppSpec·deployment group이 필요 없다. | 두 target group·listener rule·ECS infrastructure role과 두 revision을 수용할 용량이 필요하다. 비용과 Terraform 변경 위험이 커진다. |
| D. CodeDeploy blue/green | 기존 사례와 자료가 많고 task set 기반 traffic 전환이 성숙했다. | CodeDeploy application/group/AppSpec/IAM이라는 별도 제어면이 생긴다. AWS가 native ECS blue/green으로의 마이그레이션 경로를 제공하는 시점에 신규 도입 가치가 낮다. |

### 선택 반영

**C. deployment controller는 ECS로 통일하고 신규 CodeDeploy는 도입하지 않는다.**

- Core API: Amazon ECS controller의 native `BLUE_GREEN`을 별도 슬라이스로 도입한다.
- 선행 조건: 두 target group과 weighted listener rule, green을 수용할 ECS 용량, infrastructure IAM role,
  smoke lifecycle hook, ALB 5xx·unhealthy target 중심 CloudWatch rollback alarm.
- threshold는 임의 숫자로 고정하지 않고 dev 실패 주입과 live baseline 관측 뒤 결정한다.
- Worker: 같은 ECS controller를 사용하되 ALB가 없고 API traffic 전환과 수명이 다르므로 `ROLLING` 전략을 유지한다.
  waiter 뒤 `PRIMARY taskDefinition == requested`를 검증한다.

현재 AWS ECS는 `ROLLING`, `BLUE_GREEN`, `LINEAR`, `CANARY`를 ECS controller에서 직접 지원하며,
CodeDeploy task set은 별도 선택지다. 근거:
[ECS deployment controllers and strategies](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_service-options.html),
[migrate CodeDeploy to ECS blue/green](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/migrate-codedeploy-to-ecs-bluegreen.html).

재검토 조건: native blue/green 선행 리소스의 비용이 실제 live 위험보다 크다는 plan·운영 근거가 생기면
strategy만 rolling으로 되돌릴 수 있지만 controller는 ECS로 유지한다.

## 7. Terraform CI와 시크릿 이전

### 7.1 plan/apply 운영 방식

상태: **A의 무승인형으로 확정**. PR plan + merged revision 재-plan + CI 성공 뒤 자동 apply를 사용한다.

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| A. PR plan + merge 후 CI 자동 apply | 리뷰 시 변경량·replacement·IAM 확대를 보고, CI를 통과한 merged revision만 apply한다. OIDC 역할을 plan/apply로 분리할 수 있다. | 별도 사람 pause가 없으므로 protected branch·workflow/ref claim과 exact-plan 정합성이 최종 경계다. |
| B. 수동 dispatch plan/apply | 단순하고 초기 통제가 쉽다. | PR 리뷰에 plan 근거가 없고 실행 누락·로컬 작업 회귀 위험이 크다. 반복 운영이 사람 기억에 의존한다. |
| C. HCP Terraform 같은 managed runner | state lock, 정책, run history, approval을 제품에 맡길 수 있다. | 비용·공급자 종속·학습 비용이 생기며 현재 규모에는 과할 수 있다. |

### 추천

**A. GitHub Actions 기반 PR plan + merged revision 재-plan + 무승인 자동 apply**를 사용한다.

- PR(`infra/terraform/**`): fmt/validate 후 환경별 plan. AWS 리소스 mutation 권한이 없는 plan OIDC role을 쓴다.
  state read와 lock에 필요한 최소 S3/DynamoDB 권한은 별도 허용한다.
- fork PR: OIDC와 시크릿 없이 fmt/validate만 수행한다.
- merge: merged SHA에서 plan을 다시 만들고, raw plan은 공개 저장소의 GitHub artifact에 올리지 않는다.
  전용 private S3 prefix에 SSE-KMS·24시간 이내 lifecycle로 저장하고 SHA-256을 함께 기록한다. apply job은
  같은 key와 hash의 exact plan만 받아 별도 사람 승인 없이 환경별 apply OIDC role로 실행한다.
- PR 코멘트에는 raw `terraform show -json`을 붙이지 않고 add/change/destroy 수, replacement·IAM·public
  exposure 같은 위험 요약만 secret redaction 뒤 게시한다.
- `(plan no-op || apply 성공)` 뒤 별도 resumable job에서 `sync-github-variables.sh`를 실행한다. sync 실패는 apply
  실패와 구분하고, 전체 rerun의 새 plan이 no-op이어도 current state에서 sync를 다시 시도한다.
- drift: live/shared는 매일, dev는 주 1회 `plan -detailed-exitcode`를 실행한다. 차이가 있을 때만 알리고
  자동 apply하지 않는다.
- Environment는 보호 없는 variable namespace로만 쓴다. role trust는 Environment subject 외에 immutable repository ID,
  workflow, default branch ref, reusable `job_workflow_ref`를 함께 검증하고 plan/apply role을 분리한다.

GitHub OIDC는 장기 AWS access key 없이 단기 자격 증명을 사용하며, Environment를 쓰면 AWS trust의
`sub`도 environment 이름과 맞춰야 한다.
근거: [GitHub OIDC in AWS](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws).

### 7.2 OAuth·애플리케이션 시크릿 원본

상태: **B로 확정**. 시크릿은 사전 생성 SSM SecureString으로 두고 Terraform은 이름/ARN만 참조한다.

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| A. GitHub secret → 일반 sensitive `TF_VAR` → SSM | CI 연결이 쉽고 Terraform이 생성·갱신을 소유한다. | `sensitive`는 출력만 가릴 뿐 값이 Terraform state와 과거 state version에 평문으로 남는다. |
| B. 사전 생성 SSM SecureString을 Terraform이 이름/ARN으로만 참조 | Terraform state에 새 secret 값을 넣지 않고 현재 DB·Worker secret 참조 패턴과 일치한다. | 최초 생성·회전은 별도 승인 절차가 필요하고 Terraform만으로 값 drift를 판정하지 못한다. |
| C. Terraform 1.11+ ephemeral variable + `aws_ssm_parameter.value_wo` | 선언적 소유를 유지하면서 secret 값을 plan/state에서 제외할 수 있다. | 현재 `required_version >= 1.5`, AWS provider `~> 5.100` 기준을 올리고 write-only 지원·version counter·복구를 검증해야 한다. |

### 추천

**지금은 B, Terraform 업그레이드 검증 뒤 C를 재평가**한다. A는 사용하지 않는다.

- Google OAuth secret을 환경별 사전 생성 SecureString으로 옮기고, Terraform은 parameter name/ARN과
  task execution role의 read 권한만 관리한다.
- 현재 state에는 기존 secret 값이 이미 포함됐을 수 있으므로 코드 변경만으로 노출이 사라졌다고 보지 않는다.
  migration 뒤 OAuth credential을 회전하고, versioned state bucket의 과거 version 접근 권한·보존 정책을 점검한다.
- Slack webhook처럼 AWS 밖 시크릿만 GitHub secret에 남긴다.
- C를 선택하려면 Terraform >= 1.11과 provider의 `value_wo` 지원을 별도 plan으로 검증하고,
  `value_wo_version` 증가·실패 복구·rotation runbook까지 확인한다.
- 자동 회전·cross-region 복제·더 세밀한 secret 감사가 필요해지면 SSM 유지 대신 AWS Secrets Manager로
  결정을 다시 비교한다.

HashiCorp는 일반 `sensitive` 값이 state에 남을 수 있음을 명시하고, ephemeral/write-only 값은 state·plan에서
제외한다고 설명한다. AWS는 `SecureString` 값을 KMS로 암호화한다.
근거: [Terraform sensitive data](https://developer.hashicorp.com/terraform/language/manage-sensitive-data),
[AWS Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/what-is-a-parameter.html).

## 추천 적용 순서

1. main은 PR merge와 필수 CI를 유지하되 required approving reviews를 0으로 설정한다.
2. `live-app`(reviewer 없음)과 `live-infra`(apply 확인) 권한·OIDC 경계를 분리한다.
3. 개발 플랫폼 rollback bundle 계약과 GitHub rollback workflow 경계를 확정한다.
4. dev에서 capacity·alarm·smoke hook을 검증하고 Core API native ECS blue/green을 도입한다.
5. readiness + `/v1/terms` smoke와 Slack Incoming Webhook 알림을 붙인다.
6. immutable digest 기반 main 자동 live 승격을 연결한다.
7. Terraform PR plan/apply role 분리와 사전 생성 SSM secret 참조를 도입한다.

각 단계는 이전 단계의 보호 장치 없이 다음으로 넘어가지 않는다. Terraform apply와 ECS blue/green 인프라
변경은 이 문서 승인만으로 실행하지 않으며, 별도 infra-change plan과 `live-infra` 확인을 거친다.
일반 애플리케이션 승격은 별도 reviewer pause 없이 main 머지 뒤 자동 실행한다.
