# Git 워크플로

[← 허브로](README.md)

## 브랜치

- 기본 브랜치: `dev`. 기능 브랜치에서 작업하고 PR 로 합친다.
- 브랜치명은 **ASCII 만**: `feat/MOI-{issue-number}-{short-description}` (예: `feat/MOI-351-error-response-docs`).
  Linear 가 제안하는 한글 브랜치명은 쓰지 않는다.
- **PR 이 열린 브랜치는 rename 금지** — GitHub 이 PR 을 닫아버린다.

## worktree

- 작업은 **별도 worktree**에서 한다. 여러 이슈를 오가며 작업할 때 브랜치
  전환으로 빌드 산출물·IDE 인덱스가 깨지는 것을 막는다.
- 생성 전 기준 브랜치를 최신화한다.

  ```bash
  git fetch origin
  git worktree add .worktrees/moi-351 -b feat/MOI-351-error-response-docs origin/dev
  ```

- dev 변경을 반영해야 하면 worktree 안에서 리베이스한다(`git rebase origin/dev`).
  보호 브랜치(main·dev)로의 non-fast-forward push 는 훅이 차단한다.
- PR 이 머지되면 정리한다: `git worktree remove .worktrees/moi-351`.

## 커밋 메시지

**Angular 커밋 컨벤션**을 따른다. 제목·본문 한글 허용.

```text
{type}({scope}): {subject}

{선택 본문: 리뷰·후속 작업에 필요한 맥락 또는 TODO를 명사형 불릿으로 작성}
```

- type: `feat` `fix` `refactor` `docs` `test` `chore`
- scope 는 모듈 또는 개념: `(profile)` `(member)` `(catalog)` `(storage)` `(error)` `(api)`
  `(api-docs)` `(infra)`
- 제목은 명사형 종결("~추가", "~전환", "~분리")로 간결하게.
- 커밋 본문은 **선택 사항**이다. 다른 작업자가 커밋과 PR 을 이해하는 데 필요한 정보가 없으면 비워 둔다.
- 본문이 필요하면 간결한 불릿포인트와 명사형 종결 어투로 작성한다.
  - 비자명한 결정과 이유, 의도적으로 제외한 범위, 알려진 제약, 후속 TODO, 운영 시 주의사항
  - TODO 는 가능하면 후속 이슈나 다시 확인할 조건을 함께 기재
- 본문을 변경 파일이나 작업 내역의 요약 목록으로 사용하지 않는다. 제목과 diff 로 알 수 있는 내용은 반복하지 않는다.

  ```text
  feat(question): 꼬리질문 삭제 정책 반영

  - 완료된 모임의 질문 변경 제외: 읽기 전용 정책
  - TODO(MOI-000): API rate limit 기준 확정 필요
  ```

  다음처럼 작업 내역만 나열하는 본문은 작성하지 않는다.

  ```text
  - 서비스 테스트 추가
  - 저장소 메서드 수정
  - 문서 업데이트
  ```

- **em-dash(`—`) 금지.** 제목·본문 모두. 구분이 필요하면 콜론·쉼표·괄호로 푼다.
- **Co-Authored-By 트레일러(Claude 등)를 넣지 않는다.**
- 이슈 번호는 PR 제목에 붙이고(`(MOI-316)`), 커밋 제목에는 강제하지 않는다.

## 커밋 단위

- **작업 단위로 응집되게** 나눈다. "스토리지 스키마 / 에러 계층 분리 / 도메인 추가 / 컨트롤러 전환"처럼
  각 커밋이 하나의 관심사를 가진다. 리뷰 수정은 원래 관심사의 커밋에 반영한다.
  최신 커밋은 amend, 이전 커밋은 fixup 후 autosquash하며 임시 fixup 커밋은 push 전에 정리한다.
- **모든 커밋은 그 시점에 빌드·테스트가 통과해야 한다.** 커밋을 나눌 때 중간 상태를 구성해서라도
  커밋별 빌드를 보장한다(bisect 가능성).

## PR

- base 는 `dev`. 제목은 대표 커밋 스타일: `feat(profile): ... (MOI-316)`.
- 본문에 **`Closes MOI-{번호}`** 를 넣어 Linear 이슈를 자동 연결·종료한다.
- 본문 구성: 개요 / 변경 사항(PR 전체 기준 핵심 결과) / 맥락·결정 / 후속 작업·TODO /
  검증(빌드·테스트·문서 생성 확인) / 배포 노트(seed.sql 수동 반영 등 운영 액션이 있으면 반드시).
- 각 커밋 본문에 남긴 비자명한 맥락, 제약, TODO, 운영 주의사항은 PR 관점에서 취합한다.
  단순히 커밋 로그를 복사하지 않고, 중복되거나 PR 안에서 해결된 내용은 제거한다.
- 하나의 목적을 이루는 변경은 한 PR에 모은다. 독립적인 변경이 섞였거나 파일 수
  상한을 넘으면 범위를 조정하거나 PR을 나눈다.
- **스택 PR은 선택지다.** 나눈 변경 사이에 의존성이 있을 때 고려하며, 독립된
  변경은 별도 PR로 진행할 수 있다. 스택을 쓰면 기능 브랜치를 체이닝하고
  (`#13 ← #14 ← #15`) base를 앞 브랜치로 지정한다. 앞 PR이 머지되면 뒤 PR을
  리베이스하며 머지 순서도 의존 순서를 따른다. 파일 수만으로 스택을 강제하지 않는다.
- 변경 파일은 **50개 상한**이다. 상한 안에서는 목적의 응집도를 기준으로 판단하며
  파일 수만으로 잘게 나누지 않는다. 51개부터는 분할하기 전 PR을 생성·갱신하지 않는다. worklog·생성 파일도
  포함한다. Git이 50% 유사도 기준으로 rename을 감지하면 한 건, 그 미만의 이동은
  삭제와 추가로 센다. 비교 대상은 실제 PR base와 head의 merge-base다.
  계획 때 예상 범위를 확인하고, 커밋 전에는 `python3 .agents/gates/check_pr_scope.py
  --base origin/{실제-base} --worktree`, 깨끗한 최종 커밋에서는 `--worktree` 없이 확인한다.
- 스택의 중간 PR도 CI·리뷰를 통과해야 한다. 상위 base가 바뀌면 다시 검증한다.
  하나의 이슈를 여러 PR로 나눴으면 앞 PR은 `Refs MOI-{번호}`, 전체 완료 PR만 `Closes`를 쓴다.
- 머지는 리뷰어(사람)가 한다. merge commit 방식 + 브랜치 삭제:
  ```bash
  gh pr merge {N} --repo 100Thieves-team/moimyeon-backend --merge --delete-branch
  ```

## 리뷰

- 자동 리뷰(CodeRabbit)는 **타당성을 판정한 뒤** 반영한다. 전부 수용하지 않는다 —
  설계 의도와 어긋난 지적(예: 파생 사실을 하드코딩으로 오인)은 사유를 남기고 스킵한다.
- 리뷰 반영은 기존 커밋을 리라이트하고, main/dev를 제외한 작업 브랜치에만
  `--force-with-lease=refs/heads/{브랜치}:{사전에 확인한 원격 head}`로 push한다.
  일반 force와 기대값 없는 lease는 쓰지 않는다. 거부되면 원격 변경을 확인하며
  기대값만 바꿔 덮어쓰지 않는다.
- 리라이트 전후의 range-diff로 리뷰 범위를 확인하고, 의도적 리라이트와 근거 없는
  계보 분기를 구별한다. 관측한 head·비교 base·리뷰 근거는 저장소 밖 임시 파일로
  전달한다. 절차는 [review-rewrite.md](../../.agents/skills/ship-pr/references/review-rewrite.md).
- 관련 검증과 명세·설명 갱신을 마친 뒤 push하고, 최신 PR head의 체크를 확인한다.
  이전 head의 성공은 새 head의 검증이 아니다.
- 스택 PR 의 앞 브랜치를 고치면 뒤 브랜치들을 리베이스해 스택 관계를 유지한다.
