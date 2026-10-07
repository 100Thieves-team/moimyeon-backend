# 애플리케이션 로그 저장

앱은 안전한 JSON을 stdout에 쓴다. ECS FireLens의 Fluent Bit이 이를 CloudWatch와 S3에 보낸다. Kotlin 코드에는 로그 저장용 AWS 호출을 추가하지 않는다. 이 모듈은 EC2 launch type 전용이다. AWS for Fluent Bit 3.4.17(AL2023, Fluent Bit 5.0.9)을 digest로 고정한다.

| 로그 | 목적지 | 보존 정책 |
| --- | --- | --- |
| TRACE·DEBUG | 서비스별 CloudWatch debug | 3일 |
| INFO·WARN·ERROR | CloudWatch ops + S3 ops | 각각 7일·90일 |
| INFO + category=growth | S3 growth | 90일 |
| 라우터 자체 진단 | CloudWatch router | 7일 |
| provision 우회 모드의 모든 로그 | CloudWatch fallback | 3일 |

S3에는 gzip NDJSON을 10MiB 또는 1분 기준으로 묶어 PutObject 한다. 키는 `env=dev/service=core-api/retention=ops/dt=YYYY-MM-DD/hour=HH/<uuid>.json.gz` 형태다. 시간 파티션은 이벤트 시간 기준이다. 재시도·혼잡 때 1분보다 늦을 수 있고, 하나의 파일에 인접한 시간대 이벤트가 섞일 수 있다. 조회는 인접 파티션을 함께 읽고 JSON의 timestamp로 거른다. 보존 정책은 저장소의 만료 설정이며 정확히 해당 시각에 물리 삭제된다는 보장은 아니다.

현재 앱에는 그로스 이벤트 발행기가 없다. growth 분기는 목적지만 준비한다. 운영 로그도 감사 원장으로 사용하지 않는다.

## 장애가 요청 처리로 번지지 않게 한다

Docker 로그 드라이버는 non-blocking 4MiB, Fluent logger 대기열은 256개로 제한한다. 라우터는 CPU 64 shares와 메모리 128MiB, rewrite emitter 5MiB를 사용한다. S3 플러그인의 디스크 버퍼 설정은 ops 224MiB·growth 32MiB다. 이는 플러그인별 상한이며 파일시스템 quota가 아니다. FireLens가 생성하는 Forward input에는 별도 Mem_Buf_Limit을 주입하지 않는다. 프로세스 전체는 컨테이너 메모리 상한으로 제한되며, 초과 시 라우터가 종료될 수 있다.

DEBUG는 메모리에서 CloudWatch로만 간다. 원래 이벤트 시각을 유지하고 3일보다 오래된 DEBUG/TRACE를 거른다. CloudWatch 재시도는 debug 2회·ops 5회로 제한한다. S3는 플러그인 자체 버퍼와 재시도를 사용한다. 두 출력이 같은 프로세스와 emitter를 공유하므로 목적지 사이에 완전한 장애 격리가 있는 것은 아니다.

라우터는 non-essential이고 재시작 정책을 사용한다. 앱 시작은 라우터 HTTP 엔진이 HEALTHY인 경우에만 허용한다. 이 상태는 AWS 도착 성공을 뜻하지 않는다. 시작 후 라우터가 멈춰도 앱을 직접 종료하지 않으며, 드라이버 포화 이후에는 로그를 잃는다. 재시작 정책도 모든 종료를 복구하지 않는다. 60초 이전에 죽거나 프로세스는 살아 있지만 전송만 막힌 경우 별도 대응이 필요하다.

버퍼는 task 볼륨에 있다. 컨테이너 재시작에는 남을 수 있지만 task 교체·호스트 장애의 내구성을 보장하지 않는다. 종료 유예 30초 중 Fluent Bit Grace는 25초다. 장기 장애·디스크 포화·강제 종료 때 손실을 허용하며, 중복 없는 전달도 보장하지 않는다.

## 안전한 출력과 권한

개인정보를 제거하는 주체는 앱의 SafeLogFormatter다. 라우터는 JSON schemaVersion=1의 허용 필드만 재구성하며 message·body·예외 메시지·임의 MDC를 복사하지 않는다. 잘못된 JSON·일반 텍스트·알 수 없는 스키마는 버린다. 유일한 예외는 앱의 고정된 `logging.serialization_failed` 이벤트다. 필드 허용은 값의 개인정보 여부를 모두 판별하는 정규식 마스킹이 아니다. formatter를 우회하는 직접 stdout 출력은 허용하지 않는다.

로그와 설정은 별도 비공개 S3 버킷에 둔다. SSE-S3, public access block, BucketOwnerEnforced, TLS 강제 정책을 적용한다. 로그 버킷은 90일 만료, 설정 버킷은 versioning을 켜고 만료시키지 않는다.

Task runtime role에는 자기 서비스 S3 prefix의 PutObject와 해당 ops/debug 그룹의 CreateLogStream·PutLogEvents만 추가한다. 로그 읽기·삭제 권한은 추가하지 않는다. Execution role에는 설정 버킷의 자기 서비스 revision 읽기와 GetBucketLocation을 추가한다. Shared의 CI plan role은 dev 설정 revision prefix에 한해 GetObject·GetObjectTagging을 허용한다. 설정 객체의 다음 plan refresh에 필요한 권한이며 애플리케이션 로그 읽기는 열지 않는다. 라우터 자체 CloudWatch 전송은 기존 task execution managed policy를 사용한다. **ECS task role은 앱과 라우터가 공유하므로 IAM으로 둘을 분리한 구조는 아니다.**

## 배포와 롤백

`application_logging_mode`는 커밋된 환경 tfvars에서 정한다.

- `disabled`: 처음 도입하지 않은 환경. 저장소·권한·라우터를 만들지 않는다.
- `provision`: 저장소·설정·권한을 유지하고 새 task template은 라우터 없이 non-blocking awslogs로 fallback 그룹에 보낸다. DEBUG가 기존 30일 그룹에 남지 않도록 전 수준을 3일 보존한다. 이 비상 우회에서는 ops 7일·S3 보관을 제공하지 않는다.
- `enabled`: 앱 task template에 FireLens를 연결한다.

현재 dev는 enabled, live는 disabled다. dev/perf는 같은 dev ECS 저장 경로를 쓰며 앱 XML만 별도로 선택한다. staging에는 이 저장소에서 관리하는 별도 Terraform root가 없다. local·local-dev·test는 ECS 라우터를 사용하지 않는다.

이미 도입한 환경을 disabled로 바꾸면 보존 자원의 prevent_destroy가 plan을 막는다. 라우팅을 끄려면 provision을 사용한다. 단, Terraform은 ECS service의 task_definition을 직접 바꾸지 않는다. 기존 배포 파이프라인이 새 template을 받아 앱 이미지를 넣고 배포해야 실제 경로가 바뀐다. 즉시 복귀는 기존 deployment bundle의 exact task definition 롤백 절차를 따른다. 도입 전 task ARN으로 복귀하면 당시 awslogs 그룹·수준·보존 정책도 복원된다. 새 3일 정책을 유지해야 하면 이 모듈의 provision template으로 재배포한다.

설정 키는 revision·service·내용 hash를 포함하고 prevent_destroy로 보호한다. 배포한 `router/v1/`은 수정하지 않는다. 변경할 때 v2 디렉터리를 추가하고 `revisions`에 v1·v2를 유지한 채 `active_revision`만 바꾼다. **S3 versioning만으로는 삭제된 예전 키를 읽을 수 없다.** 이전 객체·이미지·GetObject 권한을 함께 남겨야 이전 task ARN으로 돌아갈 수 있다.

API task 메모리는 1600→1760MiB, Worker는 768→928MiB다. 앱 메모리는 유지하고 라우터 128MiB와 드라이버 여유 32MiB를 더했다. task CPU 총량은 유지하며 앱 shares 중 64를 라우터에 배분한다. 32MiB는 Docker/호스트가 자동으로 그만큼 예약하는 설정이 아니라 용량 계획의 여유다. 실제 EC2 registered/remaining memory·ENI·blue/green 동시 배치를 dev에서 확인해야 한다.

## 검증과 적용 전 확인

로컬 실행은 AWS 계정에 접근하지 않는다.

```bash
terraform -chdir=infra/terraform/modules/application-logging init -backend=false -lockfile=readonly
terraform -chdir=infra/terraform/modules/application-logging test
python3 infra/terraform/tests/logging_smoke.py
python3 infra/terraform/tests/test_deploy_inputs.py

# API/Worker 소비 측 task 정의 (모든 provider는 mock)
terraform -chdir=infra/terraform/modules/moimyeon-environment init -backend=false -lockfile=readonly
terraform -chdir=infra/terraform/modules/moimyeon-environment test
```

수집 모듈의 native Terraform test는 mock provider plan으로 보존·자원·모드 계약을 검사한다. 환경 모듈의 소비 측 테스트는 다른 라우터 예산을 주입하고 API·Worker task JSON의 예산·목적지·의존성·볼륨과 CPU 부족 거부를 확인한다. Docker smoke는 운영과 동일 digest의 Fluent Bit과 네트워크가 격리된 S3/CloudWatch 대역을 사용한다. 실제 gzip 객체, 로그 분류, 만료된 debug 제거, 비허용 필드 차단, S3 503 뒤 라우터 강제 종료와 같은 볼륨에서의 재시작 복구, task health check 명령을 검사한다. timeout만 1초로 줄이며 테스트용 TLS 인증서와 엔드포인트는 운영 설정에 들어가지 않는다.

이 검증은 ECS agent의 S3 설정 다운로드·IAM·실제 AWS 수신·EC2 배치 여유를 증명하지 않는다. PR CI의 sanitized plan을 먼저 판독한다. Shared의 plan refresh 권한이 dev보다 먼저 적용돼야 하며, 기존 파이프라인은 shared 적용 뒤 dev plan을 만든다. 에이전트는 apply하지 않으며, 이 저장소는 머지 후 자동 apply하므로 머지가 적용 승인이다.

Dev 배포 후에는 다음을 실제로 확인하고 live 도입 여부를 정한다.

1. API/Worker 동시 배치와 교체, 원래 HTTP/배치 기능 유지.
2. 안전한 canary의 CloudWatch/S3 도착 및 timestamp·gzip·서비스 prefix. DEBUG가 S3에 없는지 확인.
3. 라우터 종료·재시작·S3 차단·버퍼 포화·task 종료 때 요청 지연, 누락량, 도착 지연.
4. 이전 exact task ARN 복귀 시 이전 설정 객체를 읽을 수 있는지 확인.
5. 라우터 진단·컨테이너 종료·canary 미도착 감시. S3 output의 일반 Fluent Bit 성공 카운터를 도착 증거로 쓰지 않는다. 이 감시와 업무 ERROR/WARN 알림은 후속 슬라이스다.

참고: [ECS FireLens 설정과 경계](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/firelens-taskdef.html), [Fluent Bit S3 버퍼와 메트릭 한계](https://docs.fluentbit.io/manual/data-pipeline/outputs/s3), [ECS container restart 정책](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-restart-policy.html).
