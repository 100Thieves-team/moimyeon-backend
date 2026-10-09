# MOI-589 계획

[MOI-589](https://linear.app/100-thieves/issue/MOI-589) 일부 서비스만 배포해도 배포 기록·live 승격·롤백 정합. 상위 MOI-584 과제 2, MOI-590의 전제.
사용자가 MOI-584 서브이슈 처리·PR·머지를 위임(2026-10-10).

- [x] 1. 컨텍스트 — 기록은 커밋당 하나(SSM `/moimyeon/dev/deployments/<sha12>`), 내용이 다르면 거부(불변). dev 배포는 매번 새 태스크 정의를
  등록하므로 같은 커밋을 다시 배포하면 기록 단계에서 실패한다. 2026-10-09 GitHub가 같은 push를 두 번 보낸 사례(fde4fd0b)가 있었다
  (그때는 더 새 커밋이 있어 두 번째가 건너뛰어졌다).
- [x] 2. 변경 — 기록이 있으면 그 태스크 정의를 다시 배포, 기록과 지금 설정·이미지가 어긋나면 바꾸기 전에 멈춤
- [x] 3. 검증 — 가짜 aws로 찾기·기록 불변·워크플로 연결 테스트, 계약 테스트 전체, actionlint, shellcheck, qa-reviewer CONDITIONAL 반영
- [ ] 4. 커밋·PR·머지 후 dev 배포 확인, 같은 실행을 다시 돌려 기록 재사용 경로("Reusing recorded", 표식 "already exists", "already recorded") 확인
