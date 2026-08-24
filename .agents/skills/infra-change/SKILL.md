---
name: infra-change
description: infra/terraform·Dockerfile·GitHub Actions 워크플로·docker-compose 등 인프라 표면의 변경을 plan 판독과 함께 수행한다. "테라폼 바꿔줘", "ECS/RDS 설정 변경", "워크플로 수정", "인프라 변경" 요청 시 사용한다. 핵심 규칙 — plan 없는 인프라 PR 금지, apply는 하지 않는다. 애플리케이션 코드 변경은 requirement-implementation, 장애 진단은 incident-response.
---

# infra-change — 인프라 변경 (plan 필수, apply 금지)

진행 규칙: `[체크포인트]`에서는 산출물을 제시하고 **턴을 끝낸다** — 사람
승인 없이 다음 단계로 가지 않는다(사용자가 "끝까지 진행해"라고 명시하면
생략). `worklog/{이슈키}/`가 있으면 재실행이다 — plan.md 체크박스(사람이
승인한 단계에만 `[x]`)로 마지막 승인 지점을 찾아 그 다음부터 재개한다.
없으면 초기 실행이다 — worklog 디렉토리와 plan.md(단계 체크리스트)부터 만든다.

기준 문서: `docs/knowledge/infra.md` (파이프라인·Terraform 불변식, Actions
정책, plan 위험 요소). 시작 전 전체를 읽고, **불변식을 위반하는 변경을
만들지 않는다.**

## 단계

1. **worktree 준비** — `docs/conventions/git.md`.
2. **컨텍스트 수집** — `.agents/skills/issue-context/SKILL.md` 수행.
   변경 대상(모듈·환경)과 영향 환경(dev/shared/live)을 확정한다.
3. **변경 작성** — 기존 모듈 구조(`infra/terraform/modules/…`,
   `envs/…`)를 따른다. 시크릿 값은 취급하지 않는다 — tfvars 시크릿은
   SSM·CI 주입 대상이고, 값이 필요한 상황이면 정지하고 사람에게 알린다.
4. **정적 검증** — `terraform fmt -check`·`terraform validate`.
   워크플로 변경이면 infra.md의 Actions 정책(최소 권한, SHA pin,
   concurrency)을 대조한다.
5. **plan 판독** — Terraform CI 이전 완료 후에는 PR의 CI plan 코멘트를,
   그 전(과도기)에는 로컬 `terraform plan` 출력을 판독한다 — **어느
   시점에도 apply는 하지 않는다** (apply는 CI + Environment 승인 게이트,
   사람 몫). 판독 기준: infra.md의 위험 요소 목록 — replacement·데이터
   저장소 재생성·IAM 확대·`0.0.0.0/0` 등이 보이면 사유와 복구 계획을
   명시하고, live 영향은 별도 표기한다.
   **[체크포인트: plan 승인]**
6. **커밋·PR** — `.agents/skills/ship-pr/SKILL.md`를 수행한다. PR 본문에
   plan 요약(자원 추가/변경/파괴 수)과 live 영향 여부를 명시한다.

## 하지 않는 것

- `terraform apply`, 콘솔 수동 변경 — 어떤 환경에서도 실행하지 않는다.
- 시크릿 값의 열람·기록 — 키 이름 확인까지만.
- 파이프라인 불변식을 깨는 변경(CI 미종속 배포 경로, live 재빌드,
  게이트 없는 live 접근) — 요청이 그걸 요구하면 불변식과의 충돌을
  보고하고 정지한다.
