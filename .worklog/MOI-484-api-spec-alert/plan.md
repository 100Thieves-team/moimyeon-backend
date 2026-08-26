# MOI-484 API 스펙 변경 알림 계획

검증한 커밋: 없음

## 승인 체크포인트

- [x] 컨텍스트와 변경 범위 승인 (2026-08-27: dev 전용, summary·description 제외, webhook 준비 확인)
- [x] 워크플로 정적 검증 및 인프라 plan 판독 승인 (2026-08-27: Terraform 0/0/0, 사용자 승인)
- [ ] 커밋·PR 승인

체크박스는 사람이 승인한 뒤에만 표시한다.

## 자연어 흐름

1. dev 또는 main 머지로 API 문서 워크플로가 새 OpenAPI 스펙을 생성한다.
2. 같은 브랜치에 마지막으로 공개된 gh-pages 스펙과 새 스펙을 비교한다.
3. 추가·삭제·계약 변경된 HTTP method와 path가 없으면 문서만 배포하고 알림은 보내지 않는다.
4. dev의 변경된 API가 있으면 새 문서를 먼저 배포하고 목록과 브랜치·커밋·실행 링크를 Slack Incoming Webhook으로 전송한다.
   main은 문서만 게시하고 같은 계약을 다시 알리지 않는다.

실패 후 보존 결과:

- 이전 공개 스펙이 없음: 최초 기준선으로 간주해 전체 API 알림을 보내지 않고 새 문서를 배포한다.
- 스펙 비교 실패: 잘못된 변경 목록을 보내지 않고 워크플로를 실패시킨다.
- Pages 배포 실패: 기준선을 바꾸거나 Slack 알림을 보내지 않아 재실행 시 중복 알림이 생기지 않게 한다.
- Slack 전송 실패·webhook 미설정: 게시된 문서는 유지하고 Actions 경고로 남긴다.

## 변경 순서

1. 스펙 비교기의 실패 테스트와 워크플로 정적 계약을 먼저 추가한다.
2. 로컬 `$ref`를 따라 request/response 스키마 변경까지 operation 변경으로 판정하는 비교기를 구현한다.
3. Slack payload 생성·전송 스크립트를 구현한다.
4. 기존 API Docs Pages 워크플로에 이전 스펙 조회, 비교, 조건부 알림을 연결한다.
5. 변경한 외부 Action을 full SHA로 고정하고 최소 권한·기존 concurrency를 확인한다.

## 검증

- API 추가·삭제·직접 필드 변경·참조 스키마 변경·무관한 컴포넌트 변경 단위 테스트
- Slack payload 로컬 HTTP 수신 검증
- 워크플로 정적 계약 검사
- actionlint 및 YAML 파싱
- 실제 `:core:core-api:openapi3 -x check` 생성물의 동일 스펙 비교 결과 없음 확인
- `git diff --check`

현재 실행 결과:

- OpenAPI operation 비교 단위 테스트 17개 통과
- Slack payload 로컬 HTTP 테스트 3개 통과(누락 webhook, 장문 25개, 특수문자 포함)
- 실제 `:core:core-api:openapi3 -x check --rerun-tasks` 독립 2회 생성 성공, operation diff 0건
- API 스펙 알림 워크플로 정적 계약과 actionlint 1.7.12 통과
- 하네스 self-test·스킬 lint·시크릿 스캔·`git diff --check` 통과
- `./gradlew test ktlintCheck` 통과. 첫 실행은 기존 로컬 Redis 미가동으로 Worker IT 3개가 실패했고,
  해당 컨테이너를 임시 시작한 재실행에서 전체 BUILD SUCCESSFUL 확인 후 원래 중지 상태로 복원
- reviewer 1차 지적: 동적 example 오탐, core-enum trigger 누락, publish 실패 후 Slack 중복
  재현 후 테스트 우선 수정 완료
- reviewer 2차 지적: 실제 필드명이 `example(s)`인 계약 누락 재현 후 named-map 문맥 보존으로 수정 완료
- code-reviewer 사용자 피드백 반영 감사: 필수 지적 없음, 문서 정합성 권장 1건 반영 완료
- qa-reviewer 최종 PASS: 지적·coverage gap 없음
- Terraform 파일 변경 없음: `0 add / 0 change / 0 destroy`, AWS·Slack 운영 리소스 변경 없음

운영 조건:

1. 완료: 프론트엔드·백엔드 공용 채널의 Incoming Webhook을 `SLACK_API_SPEC_WEBHOOK_URL` repository secret으로 등록했다.
2. 유지: Slack 실패가 Pages 게시를 되돌리지 않는 non-blocking 정책을 사용한다.
3. 완료: Slack 알림은 dev 전용으로 제한한다.

## 영향

- 환경: GitHub Actions와 Slack 알림만 변경하며 AWS·Terraform·애플리케이션 런타임은 변경하지 않는다.
- 외부 소비자: 프론트엔드·백엔드 팀원이 API 계약 변경 목록을 Slack에서 받는다.
- 시크릿: 새 이름 `SLACK_API_SPEC_WEBHOOK_URL`만 참조하며 값 생성·조회·기록은 사람이 수행한다.
