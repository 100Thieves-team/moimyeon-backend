# 미결정 및 배포 전제 조건

## SSM 사전 생성 (운영자)

- `/moimyeon/dev/core-api/SENTRY_DSN`
- `/moimyeon/dev/core-worker/SENTRY_DSN`
- `/moimyeon/dev/monitoring/GRAFANA_ADMIN_PASSWORD`

SecureString 값은 코드, PR, plan, 채팅에 작성하지 않는다. 에이전트는 값 대신 이름/ARN만 취급한다.
사용자에게 비동기로 생성 여부와 기존 파라미터 이름을 요청했다.
이 전제 조건 충족 확인 전 머지/배포는 진행하지 않는다.

## 프론트엔드

구성도에는 프론트 Sentry SDK가 포함되지만 저장소가 지정되지 않았다.
현재 백엔드 작업 범위를 넘어 임의 저장소를 변경하지 않는다.

## 실행 경계

PR 초안과 CI plan에 사람 승인 필요. 직접 apply, 우회 배포, main/live 변경 금지.
단일 EC2는 고가용성 구성이 아니다. 보존된 EBS가 있더라도 AZ 장애 자동 복구는 제공하지 않는다.
