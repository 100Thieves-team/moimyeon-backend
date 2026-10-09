# MOI-588 계획

결정(사람): 러너에서 jar 빌드, Docker는 jar 복사·레이어 추출·AOT 학습만. 캐시 범위는 방식에 맞춰 판단.
QA 필수 지적(자격증명 분리)은 사람이 "최소 완화" 선택. 이후 PR·머지까지 사용자가 위임(2026-10-10).

- [x] 1. worktree 준비 — `chore/MOI-588-image-build-cache`
- [x] 2. 컨텍스트 수집 — context.md
- [x] 3. 변경 작성
  - bootJar를 만들고 고정 이름으로 모으는 공용 스크립트(CI·배포 대체 빌드가 함께 씀)
  - Dockerfile: Gradle 빌드 단계 제거, 빌드 컨텍스트의 jar만 받아 추출·AOT 학습
  - ci.yml `image` job: JDK·setup-gradle → 스크립트 → 이미지별 빌드, 캐시 scope 이미지별·`mode=min`
  - deploy-aws.yml 대체 빌드: 빌드가 필요할 때만 JDK·Gradle 준비 후 필요한 jar만 빌드
  - 계약 테스트·`docs/knowledge/infra.md` 불변식 문구 갱신
- [x] 4. 대상별 검증 — 계약 테스트, 로컬 이미지 빌드 2종, actionlint, `./gradlew test ktlintCheck`, qa-reviewer PASS
- [x] 5. Terraform plan — 비대상(Terraform 구성·입력 변경 없음)
- [ ] 6. 커밋·PR — PR CI에서 image job 시간·캐시 적중 확인
