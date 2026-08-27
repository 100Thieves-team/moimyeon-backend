# 구현 결정

## allowlist 확대 대신 검사 범위를 PR·push 변경분으로 제한
- 날짜: 2026-08-27
- 내용과 이유: 실패한 merge commit에서 누출이 재현되지 않아 특정 fingerprint를 예외 처리할 근거가 없다. 기존 `Compute gate scope`가 계산한 범위를 Gitleaks도 공유해 unrelated history·ref 변화에 따른 오탐을 제거하고, 실제 변경분에는 기본 규칙을 그대로 적용한다.
- 대안: 탐지 후보나 worklog SHA를 allowlist에 추가. 실제 finding을 특정하지 못한 상태에서 예외를 넓혀 미탐을 만들 수 있어 제외한다.

## 범위 없는 새 ref는 HEAD에 도달 가능한 이력을 전수 검사
- 날짜: 2026-08-27
- 내용과 이유: 비교 커밋이 없는 상황에서 임의의 `HEAD~1`을 쓰지 않고 `HEAD`에 도달 가능한 전체 Git 이력을 검사한다. 여러 커밋을 한 번에 push하면서 중간에 추가 후 삭제한 시크릿도 놓치지 않고 root commit에도 동작한다. 다른 remote ref 이력은 포함하지 않는다.
- 대안: 모든 Git 이력을 검사. PR과 무관한 과거 fingerprint와 ref 집합에 결과가 흔들려 제외한다.
