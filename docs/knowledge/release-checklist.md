# 릴리스 체크리스트

> 운영 방식: 빈 목록에서 시작해 **사고·리뷰에서 잡힌 것마다 한 줄씩**
> 자란다 (release-checklist는 우리가 물린 것의 목록이지 일반론이 아니다).
> 초기 시드는 전문 에이전트 블루프린트 §5의 평가 시나리오와
> `docs/knowledge/qa-review.md` 특수 검증에서 추렸다.

머지·배포 전 확인:

- [ ] 타 사용자 ID로 조회·수정·삭제가 막혀 있는가 (BOLA — qa-review.md 3번)
- [ ] 일반 사용자가 관리자 API를 호출할 수 없는가 (BFLA — qa-review.md 4번)
- [ ] 재시도 시 데이터가 중복 생성되지 않는가 (qa-review.md 1·2번)
- [ ] 컬럼 삭제·rename이 구버전 앱과 공존 가능한가 — expand-contract
      (erd-design.md, db-reviewer ddl_analysis)
- [ ] pagination 정렬이 안정적인가 (qa-review.md 7번)
- [ ] seed.sql 등 수동 반영이 필요한 운영 액션이 PR 배포 노트에 있는가
      (git.md PR 규칙)

## 이력

- 2026-08-24: 초기 시드 5항목 (MOI-474 Step 4).
