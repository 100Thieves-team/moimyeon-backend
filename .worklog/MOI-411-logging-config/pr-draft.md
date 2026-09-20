# feat(logging): 안전한 요청 로그와 S3 저장 경로 추가 (MOI-411)

## 개요

기존 자유 형식 stdout 로그를 안전한 공통 출력으로 정리하고, 실제 HTTP 처리 완료 시 요청 요약을 한 번 남긴다. dev ECS API·Worker의 로그 저장은 FireLens가 맡는다. 애플리케이션에 S3 로그 업로드 코드를 추가하지 않는다.

Closes MOI-411

Draft PR: 실제 Terraform plan 판독과 적용 판단 전까지 draft를 유지한다. Dev 수신·배치·포화 검증은 배포 후 수행하며, 후속 알림·그로스 범위는 아래에 구분한다.

## 변경 사항

- kotlin-logging 공통 의존, 환경별 Logback XML, 안전한 JSON/text formatter와 설정 검증.
- 본문을 캐싱하지 않는 HTTP 완료 로그. 서버 requestId, 기존 trace/span, 등록된 route, 최종 status/errorCode, 소요 시간 기록.
- dev FireLens → CloudWatch ops 7일/debug 3일, S3 ops/growth 90일. 비공개·암호화·TLS·서비스 prefix 권한 적용.
- 버전별 S3 설정 파일 보존, 이미지 digest 고정, non-essential 라우터와 제한된 버퍼, 3일 보존의 비상 우회 경로.
- Terraform mock plan·실제 Fluent Bit 장애/재시작 테스트·배포 설정 보존 테스트를 CI에 연결.

## 맥락과 결정

원문을 정규식으로 모두 가릴 수 있다고 가정하지 않는다. 허용 필드와 고정 eventCode만 출력하며 예외 메시지·본문·임의 MDC를 내보내지 않는다. 자유 형식 진단 정보가 줄어드는 대가는 수용했다.

앱은 stdout 계약만 지키고 저장소 전송은 외부 라우터가 맡는다. 일반 로그는 서비스 가용성을 우선하므로 버퍼 포화·task 소실 때 유실될 수 있다. 감사 원장을 대신하지 않는다.

설정 객체는 revision·service·내용 hash로 고정한다. 이전 객체와 읽기 권한을 유지해야 과거 task ARN 롤백이 가능하다. 라우팅을 끌 때는 provision 모드로 저장소를 유지하며 전체 로그를 3일 fallback 그룹에 보낸다. 그동안 ops 7일·S3 보존은 제공하지 않는다.

[팀 로깅 아키텍처·사용 가이드](https://wiki.agent.plady.io/topics/t-moimyeon-로깅-아키텍처와-사용-가이드/)

상세 결정과 운영 절차: `.worklog/MOI-411-logging-config/decisions.md`, `infra/terraform/modules/application-logging/README.md`.

## 검증

- 전체 `./gradlew test ktlintCheck` 통과.
- Terraform 1.15.9 fmt 및 shared/dev/live validate 통과. 기존 Redis service discovery deprecation 경고는 남아 있다.
- AWS mock provider의 4개 plan 시나리오 통과.
- 격리한 실컨테이너에서 gzip JSON, 수준별 분류, DEBUG 만료, 허용 필드, S3 503·강제 종료 뒤 디스크 복구 확인. 프로세스 생존 HTTP 명령도 검사.
- 배포 입력 회귀 테스트 17개와 인프라 셸 계약 검사 통과.
- 실제 AWS plan·ECS 실행 검증은 미수행.

## Plan·배포 노트

**PR CI의 sanitized plan 수치: 아직 없음.** 로컬 mock 결과를 실제 추가/변경/삭제 수로 대신하지 않는다.

예상 변경은 dev 로그 저장소·그룹·권한 신규 구성, API/Worker task template 등록, shared CI plan 역할의 dev 설정 revision 읽기 권한이다. DB·네트워크·기존 CloudWatch 그룹은 변경 대상이 아니다. live tfvars는 disabled이며 live 활성화는 이번 대상이 아니다.

Shared 권한 적용 뒤 dev plan을 만드는 기존 순서를 유지한다. API task 메모리는 1760MiB, Worker는 928MiB가 되며 실제 EC2 배치 여유를 dev에서 확인한다. Terraform은 ECS service의 실행 task를 직접 바꾸지 않는다. 기존 배포 파이프라인이 새 template을 소비한다.

**머지 후 push CI 성공·최신 revision/plan 검사·환경별 적용 게이트 통과 시 자동 apply가 이어진다. 머지는 인프라 적용으로 이어질 수 있는 승인이다.** 에이전트 apply·배포·운영 변경은 수행하지 않았다.

## 후속 작업

- 실제 CI plan의 replacement·IAM 확대·live 영향 판독, dev 도착 canary와 용량·포화·롤백 검증.
- 라우터 장애/미도착 감시, ERROR 첫 발생 즉시·반복 묶음, WARN 분당 5회와 일일 보고 연결.
- 정상 업무 거절 수준 정리, Sentry trace 연결, 일반 비동기 작업 MDC 전달.
- SID·UTM·그로스 발행기는 별도 구현. live 수신자는 팀원 3명 전원이며 실제 수신 계정·채널은 연결 전 확인.
