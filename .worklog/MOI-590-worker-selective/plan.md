# MOI-590 계획

[MOI-590](https://linear.app/100-thieves/issue/MOI-590) Worker 입력이 바뀔 때만 교체. 상위 MOI-584 과제 1.
선행: MOI-589(#190, 배포 기록 재시도), MOI-591(#187, redis-core 분리). 사용자가 처리·PR·머지를 위임(2026-10-10).

- [x] 1. 컨텍스트 — 최근 배포 58건 중 31건은 Worker 코드가 그대로인데 교체(dev Worker 1대, 최소 정상 비율 0% → 교체 중 알림 소비 멈춤)
- [x] 2. 변경 — 산출물 입력 해시 label + 원본 틀 비교로 판단, 유지한 Worker를 기록에 이어 적기, 알림 표시
- [x] 3. 검증 — 단위·계약 테스트(dev 실제 형태인 태그 이미지 포함), actionlint, shellcheck, qa-reviewer CONDITIONAL(필수 1: 유지 커밋 재시도) 반영
- [ ] 4. PR CI에서 label 읽기 확인(실제 ECR), 머지 후 다음 API 전용 커밋에서 Worker 유지·기록·표식 확인
