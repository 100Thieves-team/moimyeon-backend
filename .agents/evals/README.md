# evals — 하네스 측정 자산

잠정 결정을 레포 기준 측정으로 확정하고(DR-012), 새 스킬의 효과를
With/Without으로 검증하기 위한 자산이다. 결과는 `.worklog/{작업키}/evals/`에
커밋한다.

## 구성

```text
trigger/   # 라우팅·트리거 검증: 양성/음성/경계 프롬프트 (md=설명, tsv=러너 입력)
ab/        # With/Without 태스크: task.md(과제), assertions.md(판정 기준)는 정량 판정이 가능한 태스크에만
run.sh     # 읽기 전용 헤드리스 러너 (claude -p, MCP 비활성화)
```

## 실행 원칙

- 결과 기록에는 반드시 재현 조건을 남긴다: 날짜, 런타임·버전, 모델, 반복 수 N.
- 트리거 측정은 두 시점에 한다:
  1. **베이스라인** — AGENTS.md 라우팅 등록 전 (자발 트리거만)
  2. **등록 후** — Step 6에서 라우팅 표 등록 뒤 재측정
  전후 차이가 곧 우리 레포에서의 라우팅 표 효과다 (DR-004 확정 근거).
- With/Without 태스크는 워크트리에서 격리 실행한다 (레포 오염 방지).
- 토큰: Claude는 `--output-format json`의 usage 필드, Codex는 실행 출력의
  `tokens used`를 수집한다.

## 실행

격리 워크트리가 **필수 인자**다 (레포 루트 실행은 거부된다 — DR-030).

```bash
git worktree add /tmp/eval-wt --detach HEAD
.agents/evals/run.sh trigger claude 1 /tmp/eval-wt requirement-implementation /tmp/eval-results sonnet
```

모델은 `--model`로 명시한다 — 헤드리스 CLI는 세션 모델 설정을 따르지 않는다
(교훈 10). 확정 집계는 `score.py`로 한다.

출력 경로와 모델은 필수다. 결과를 남길 때는 현재 작업의 worklog에 요약하며
과거 MOI-474 경로에 새 결과를 쓰지 않는다. Claude 트리거 평가는 restricted
모드에서 Read/Glob/Grep/Skill만 허용하고 MCP를 끈다(지원 CLI 필요).
외부 쓰기·게시·push는 실제 서비스 대신 시나리오 자료로 검증한다.

Codex CLI 경로는 차단한다. `--sandbox read-only`는 연결된 MCP의 외부 쓰기를
격리하지 않으므로 제품 명세 갱신 프롬프트를 그대로 실행하지 않는다. Codex에서는
외부 조회·쓰기가 없는 네이티브 독립 세션에 읽기 전용 라우팅/가상 시나리오를
맡기고 실제 선택·판정을 기록한다. 외부 CLI 측정과 구분하며, 과거 Codex raw의
오프라인 집계 기능은 score.py에 남긴다.
