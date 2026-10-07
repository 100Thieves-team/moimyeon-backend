# MOI-581 live 인프라 구성 계획

## 승인 체크포인트

- [x] 단계 계획 승인 (2026-10-07: 결정대로 작업하고 사람 작업만 안내하도록 위임)
- [ ] 1단계 live 신규 환경 plan 승인 (main 릴리스 머지 = live apply 승인)
- [ ] 2단계 HTTPS plan 승인
- [ ] 3단계 용량·Redis·Worker 활성화 plan 승인
- [ ] 커밋·PR 승인 (PR마다)

체크박스는 사람이 승인한 뒤에만 표시한다.

## 결정 (2026-10-07)

- live는 현재 팀 AWS 계정에 만든다.
- 도메인·HTTPS: dev와 같이 외부 DNS(Cloudflare) + HTTPS.
- 첫 활성화에 알림 Worker·Redis 포함, 로그 수집 `enabled`, 용량은 dev와 같은 크기로 시작.

## 확인한 사실

- live 적용은 main 머지 때만 일어난다. main은 2026-07-21 이후 그대로이고 dev보다 804커밋 뒤에 있다.
- live Terraform CI(`MOIMYEON_TERRAFORM_LIVE_CI_ENABLED`)는 2026-10-07 사람이 켰다. PR plan과 매일 drift 감지에 live가 포함된다.
  1단계 적용 전까지 live drift는 전부 생성으로 나와 실패하는 것이 예상된 상태다.
- live 승격(`MOIMYEON_LIVE_DEPLOY_ENABLED`)은 false, 배포 기록(`MOIMYEON_DEPLOYMENT_LEDGER_ENABLED`)은 미설정이라 main 머지에도
  Promote Live는 건너뛴다.
- 외부 DNS에서는 인증서가 HTTPS와 무관하게 먼저 만들어지고, HTTPS를 켜는 적용이 DNS 검증 완료를 기다린다.
  그래서 HTTPS는 검증 레코드를 등록한 뒤 별도 단계로 켠다.
- ECS는 첫 이미지가 live ECR에 올라오기 전에는 태스크를 띄울 수 없다. live 이미지는 승격(MOI-512)으로만 들어오므로
  용량 활성화와 첫 승격은 함께 계획한다.
- 문서상 live Redis는 활성화 전 HA(복제/Sentinel)가 필요하다고 적혀 있다. 3단계 전에 결정이 필요하다.

## 단계

### 1단계: live 신규 환경 생성 (용량 0)

- 사람(완료): live OAuth 클라이언트 생성, SSM `JWT_SECRET`·`GOOGLE_OAUTH_CLIENT_SECRET` 등록, live Terraform CI 플래그 켜기.
- 이 PR: live.tfvars의 비밀이 아닌 설정 반영(업로드 CORS 오리진, live OAuth client ID, SES 발신 주소, 로그 수집 enabled).
- 에이전트: live plan 판독(신규 생성만, 삭제·교체 없음, 공개 노출·IAM 범위 확인) → 보고.
- 사람: dev 머지 → dev→main 릴리스 PR 머지 → Terraform Apply(main)가 live를 만들고 변수를 동기화한다.
- 에이전트: 적용 결과·동기화된 live 변수 확인, 다음 plan 무변경 확인.

### 2단계: HTTPS

- 사람: **1단계 적용 후 72시간 안에** Cloudflare에 인증서 검증 CNAME과 앱 CNAME 등록(DNS only). ACM은 72시간 안에
  검증되지 않은 요청을 만료시킨다. 넘기면 인증서를 다시 만들어야 하므로(replace) 이 단계에서 새 검증 값을 받는다.
- 에이전트: `enable_https = true` PR → plan 판독.
- 사람: dev·main 머지.

### 3단계: 용량·Redis·Worker 활성화 (MOI-512 첫 승격과 함께)

- 사람: 앱 DB 사용자 생성, live 시크릿(DB 비밀번호·Firebase·Gmail·Redis) 등록, live Firebase 프로젝트.
- 에이전트: 용량·Redis·Worker·Firebase·Gmail 설정 PR, 승격 활성화 준비(MOI-512)와 함께 계획.

## 검증

- 각 PR: `terraform fmt -check`, validate, 설정 계약 검사, PR CI plan 판독
- 각 적용 후: 다음 plan 무변경, drift 감지 성공
- 3단계 후: live smoke(헬스·공개 읽기)

## 하지 않는 것

- `terraform apply`, 콘솔 변경, 시크릿 값 열람·기록은 에이전트가 하지 않는다.
- 승격·rollback 활성화(MOI-512·513)는 이 계획의 3단계와 맞물리지만 각 이슈에서 다룬다.
