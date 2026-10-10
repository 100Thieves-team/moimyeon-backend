# MOI-593 계획

[MOI-593](https://linear.app/100-thieves/issue/MOI-593) CI 검증 범위 조정. 상위 MOI-584 과제 6.
사용자가 MOI-584 서브이슈 처리·PR·머지를 위임(2026-10-10).

- [x] 1. 컨텍스트 — PR #186에서 CodeRabbit의 본문 수정(`edited`)이 CI 전체(build 7분 + 후보 이미지 재push)를 다시 돌린 것을 확인
- [x] 2. 변경
  - PR `edited`(base 변경 아님)에서는 배포와 무관한 job만 건너뜀. build·후보 이미지는 계속 실행(D-1)
  - 문서만 바뀐 PR은 후보 이미지를 만들지 않음(`image-scope` job, 공용 판정 스크립트)
  - Gradle build cache 사용. Testcontainers 모듈의 test는 캐시 제외
- [x] 3. 검증 — 계약 테스트(일부러 어긴 사본에서 실패 확인), actionlint, 로컬 FROM-CACHE·Testcontainers 모듈 제외 확인,
  qa-reviewer 1차 BLOCK(필수 체크 건너뛰기) 반영
- [ ] 4. 커밋·PR·머지 후 PR CI 시간과 다음 PR의 캐시 적중 확인
