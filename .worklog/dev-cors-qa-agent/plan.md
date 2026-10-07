# dev CORS 허용 목록에 qa.agent 오리진 추가

- [x] 변경 착수: 사용자 요청 "개발서버의 cors 허용 목록에 https://qa.agent.plady.io/ 이것도 넣어줘" (2026-09-22).
- [ ] PR 초안 승인.

범위: dev 프로파일 API CORS 허용 목록(`security-core.yml`)과 대응 테스트만 변경한다.
S3 presigned 업로드 CORS(terraform `upload_cors_allowed_origins`)는 요청 범위 밖이라 건드리지 않는다.
별도 worktree: `.worktrees/dev-cors-qa-agent`, branch: `feat/dev-cors-qa-agent-origin`.
연결 이슈 없음. 이슈 번호를 지어내지 않는다.

## 검증·리뷰 결과

- `./gradlew test ktlintCheck` 통과 (2026-09-22).
- qa-reviewer: PASS, 위험 low. 권고 3건 중 auth.md CORS 서술 갱신은 같은 PR에 반영.
  나머지(QA 프론트의 OAuth 콜백 오리진 확인, S3 업로드 CORS 미포함)는 PR 본문 후속 작업에 기재.
