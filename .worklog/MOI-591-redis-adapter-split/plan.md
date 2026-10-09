# MOI-591 계획

[MOI-591](https://linear.app/100-thieves/issue/MOI-591) redis-core의 API·Worker 어댑터 분리. 상위 MOI-584 과제 5.
사용자가 MOI-584 서브이슈 처리·PR·머지를 위임(2026-10-10). 정리 방식은 decisions.md D-1.

- [x] 1. 컨텍스트 — redis-core가 core-api·admin-api에 컴파일 의존. Worker는 `isTransitive = false`로 막았지만
  빌드 시 core-api 컴파일이 필요하고, core-api 변경이 Worker 산출물에 영향
- [x] 2. 변경 — API·어드민 계약 구현 3개와 설정 1개를 새 모듈 `storage:redis-api-adapter`로 이동
- [x] 3. 검증 — `./gradlew test ktlintCheck`, Worker 런타임 클래스패스에 core-api·admin-api 없음,
  `:core:core-worker:bootJar` 태스크 그래프에 core-api·admin-api 없음, code-reviewer 통과·qa-reviewer PASS(권장 반영)
- [ ] 4. 커밋·PR·머지 후 dev 배포 확인
