# MOI-594 계획

[MOI-594](https://linear.app/100-thieves/issue/MOI-594) Worker 교체 중 소비 공백·준비 상태 확인. 상위 MOI-584 과제 7.
사용자가 MOI-584 서브이슈 처리·PR·머지를 위임(2026-10-10).

- [x] 1. 컨텍스트 — Worker는 웹 서버가 없어 상태 검사가 없고, 배포 성공을 "태스크가 RUNNING"으로만 판단한다.
  dev Worker 1대, 최소 정상 비율 0%. 과거 여유 용량 부족으로 교체 태스크가 뜨지 못한 장애(operations.md)
- [x] 2. 변경 — 소비 주기 완료마다 하트비트 파일 갱신, ECS 컨테이너 상태 검사가 5분 안의 하트비트를 준비로 판단
- [x] 3. 검증 — Worker 단위 테스트(하트비트 기록·실패 시 미기록·쓰기 실패 무시·조립), 전체 테스트·ktlint, Terraform 모듈 테스트 추가(CI에서 실행),
  안정 대기 테스트(상태 검사 전 대기), qa-reviewer CONDITIONAL 반영
- [ ] 4. PR CI(Terraform test·plan), 머지 후 dev 배포에서 Worker 태스크 HEALTHY 확인
