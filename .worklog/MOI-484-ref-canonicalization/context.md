# MOI-484 `$ref` 이름 오탐 컨텍스트

별도 Linear 이슈 없이 [MOI-484](https://linear.app/100-thieves/issue/MOI-484/api-spec-%EB%B3%80%EA%B2%BD-alert-%EC%B6%94%EA%B0%80)의
병합 후 오탐을 수정하는 후속 작업이다.

## 증상

- [PR #106](https://github.com/100Thieves-team/moimyeon-backend/pull/106) 병합 SHA `5e7b74cc4854`에서 Slack이 API 64개 변경을 알렸다.
- PR #106의 실제 API 계약 변경은 `POST /v1/auth/dev-sessions` 응답의 cookie 제거와 `data.accessToken` 추가다.
- API Docs Pages run `33057371810`은 성공했고 gh-pages 게시 자체도 정상이다.

## 재현과 원인

- 직전 공개 스펙 gh-pages commit `9c3b3ba1`과 현재 `c9e21647`을 기존 비교기로 비교하면 64개가 `CHANGED`다.
- 안정적인 `GET /get/{exampleValue}`의 확장 내용은 같고 로컬 component 이름만 달라졌다.
  - 이전: `#/components/schemas/post191457252`
  - 이후: `#/components/schemas/get-exampleValue191457252`
- 비교기는 local `$ref`를 해석한 `$resolved`와 원래 `$ref` 문자열을 모두 fingerprint에 넣어 이름 재배치를 계약 변경으로 오인한다.
- 진단용으로 raw `$ref`만 제외하면 변경은 `POST /v1/auth/dev-sessions` 1건만 남는다.

## 관련 코드

- `.github/scripts/openapi_operation_diff.py`: local `$ref` 확장과 operation fingerprint 생성
- `.github/scripts/test_openapi_operation_diff.py`: 오탐·미탐 회귀 테스트
- `.github/workflows/api-docs-pages.yml`: 직전 gh-pages 스펙과 새 스펙 비교·Slack 알림
- `docs/knowledge/operations.md`: 실제 운영·CI 사건 교훈

## 작업 경계

- OpenAPI 생성기와 생성된 component 이름을 바꾸지 않는다.
- API 계약·Controller·DTO·Service를 바꾸지 않는다.
- 이미 발송된 Slack 메시지를 삭제·수정하지 않는다.
- Slack 재전송 상태 영속화와 정규식 계약 검사 구조화는 별도 후속 범위다.
