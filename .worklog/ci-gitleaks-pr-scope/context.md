# 이슈 없음: CI Gitleaks 오탐 개선

## 증상

- PR #106의 최신 head `5b5c7037`에서 CI run `32995457172`가 `build` job의 `Scan repository secrets` 단계에서 `leaks found: 1`로 실패했다.
- 같은 run의 `harness-gates` secret scan은 통과했고, 직전 head `599d2996`의 CI도 통과했다.
- 실패한 synthetic merge commit `84faee34`를 동일한 Gitleaks image digest로 로컬 재검사했을 때는 누출 0건으로 재현되지 않았다.

## 현재 구조

- `harness-gates`는 `Compute gate scope`에서 PR이면 base branch와 HEAD의 diff 범위를 한 번 계산하고, repo-local scanner가 그 범위만 검사한다.
- `build`의 Docker Gitleaks는 계산된 범위를 공유하지 않고 매번 전체 Git 이력 500여 개 커밋을 다시 검사한다.
- `.gitleaks.toml`과 `.gitleaksignore`가 존재하지만 CI 명령이 경로를 명시하지 않아 container의 작업 디렉터리와 도구 기본값에 의존한다.
- Gitleaks를 옮길 `harness-gates` checkout은 mutable `actions/checkout@v4`라 기존 build의 full SHA pin보다 공급망 보장이 약하다.

## 변경 경계

- GitHub Actions CI의 secret scan 범위와 호출 위치만 변경한다.
- 애플리케이션·Terraform·AWS·배포 workflow·시크릿 값은 변경하지 않는다.
- 실제 PR 변경분과 현재 트리의 시크릿 탐지 강도는 낮추지 않는다.
