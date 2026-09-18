# MOI-497 로컬 실호출 검증

검증일: 2026-08-31

## 환경

- 애플리케이션: `local-dev`, `http://127.0.0.1:18080`
- MySQL 8.0: 검증 전용 `--rm` 컨테이너, host port 13306
- Redis 7.4: 검증 전용 `--rm` 컨테이너, host port 16379
- 회원: `019daf00-0000-7000-8000-000000000101`
- 완료 이력서: `01920000-0000-7000-8000-000000000102`
- 개발 세션 API로 Bearer 토큰을 발급했으며 토큰 값은 기록하지 않았다.

## 성공 시나리오

```http
POST /v1/members/me/resumes/01920000-0000-7000-8000-000000000102/make-default
Authorization: Bearer [redacted]
```

```http
HTTP/1.1 200 OK
```

```json
{
  "data": null,
  "error": null,
  "result": "SUCCESS"
}
```

DB 후속 확인:

```text
resumeId: 01920000-0000-7000-8000-000000000102
is_default: 1
```

## 오류 시나리오

```http
POST /v1/members/me/resumes/01920000-0000-7000-8000-000000000404/make-default
Authorization: Bearer [redacted]
```

```http
HTTP/1.1 404 Not Found
```

```json
{
  "data": null,
  "error": {
    "code": "E1010",
    "data": null,
    "message": "이력서를 찾을 수 없습니다."
  },
  "result": "ERROR"
}
```

검증 후 애플리케이션과 검증 전용 컨테이너를 종료했다.
