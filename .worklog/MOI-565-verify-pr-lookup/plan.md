# MOI-565 후속: PR CI 검증의 머지 PR 조회 수정 — plan

결정·배경은 `.worklog/MOI-565-deploy-speed/`를 따른다. 이 작업은 #160 머지 후 첫 dev push 배포 실패의 수정이다.

- [ ] 1. 원인 확인 — API 버전 `2026-03-10`이 PR `merge_commit_sha`를 `null`로 돌려줌(`2022-11-28`은 채움)
- [ ] 2. 수정 — 머지된 PR 후보 중 head 트리가 머지 트리와 같은 PR을 선택, 약 2분 재시도
- [ ] 3. 검증 — 시나리오 테스트(fixture를 실제 응답 모양으로), 실제 API로 e2224d49 검증 통과
- [ ] 4. PR

## 실행한 검증

- `verify-pr-ci.sh e2224d49… dev`를 실제 GitHub API로 실행: PR #160, 트리 307fd3c, 후보 태그 run 37546110247-1 확인.
- `infra/terraform/tests/*.sh` 9종, `test_deploy_*.py` 17건, actionlint 통과. Kotlin 변경 없음(CI build가 gradle 검사 수행).
