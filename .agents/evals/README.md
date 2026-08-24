# evals — 하네스 측정 자산

잠정 결정을 레포 기준 측정으로 확정하고(DR-012), 새 스킬의 효과를
With/Without으로 검증하기 위한 자산이다. 결과는 `.worklog/{이슈키}/evals/`에
커밋한다.

## 구성

```
trigger/   # 라우팅·트리거 검증: 양성/음성/경계 프롬프트 (md=설명, tsv=러너 입력)
ab/        # With/Without 태스크: task.md(과제), assertions.md(판정 기준)는 정량 판정이 가능한 태스크에만
run.sh     # 헤드리스 러너 (claude -p / codex exec)
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

```bash
.agents/evals/run.sh trigger claude 3   # 트리거 세트를 claude로 3회 반복
.agents/evals/run.sh trigger codex 3
```

러너는 초안이다 — 첫 실행에서 감지 휴리스틱(스킬 경로 언급 여부)을 실측으로
보정한다.
