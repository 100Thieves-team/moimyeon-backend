# MOI-595 결정 기록

## D-1: dev 배포 스크립트는 공용 스크립트로 합치지 않는다

- 이슈는 "MOI-590을 구현할 때 하나로 합치면 조건부 배포를 넣기 쉽다"고 했다. MOI-590은 합치지 않고 구현했다(판단을
  `decide-worker-change.sh`로 빼서 테스트).
- dev에만 있는 단계: API 안정화와 병렬인 Worker 대체 빌드, ALB 대상 상태 진단, API SSM 갱신 실패 시 복원, Worker 선택 교체·이어 적기,
  같은 커밋 재시도의 기록 재사용. live 승격·롤백은 정확한 revision을 배포하는 단순 경로라, 합치면 live 경로에 dev 분기가 섞여 회귀 위험이 크다.
- 대신 두 경로가 함께 써야 하는 판단은 스크립트로 뺐다: 런타임 변경(`runtime-changes.sh`, dev·live), 배포 기록 쓰기(`record-…`, dev·live).
  기록 찾기(`find-…`)는 dev만, 기록 읽기(`read-…`)와 기한 있는 안정 대기(`lib/ecs-stable-wait.sh`)는 live 승격·롤백만 쓴다(qa-reviewer 지적으로 정정).
  dev 복원 단계의 `services-stable`(10분 상한) 대기를 공용 대기로 바꿀지는 별도로 판단한다.

## D-2: core-batch는 모듈을 두고 "배포 경로 없음"을 명시한다

- 이슈의 [결정 필요] 세 안 중 두 번째. 예제 잡 하나뿐이라 지금 배포 경로를 만들 근거가 없고, 모듈을 지우면 실제 잡이 생길 때 다시 만들어야 한다.
- 배포하게 되면 Flyway를 꺼야 한다. 지금 core-batch 설정에는 Flyway 끄기가 없어, 그대로 배포하면 core-api 외의 두 번째 마이그레이션 경로가 된다.

## D-3: ECR 이미지 만료 규칙은 이번에 넣지 않는다

- 배포 표식(`deployed-{env}-{sha12}`) 이미지는 배포 기록이 가리키는 롤백·승격 대상이라 지우면 안 된다.
- ECR 문서(Lifecycle policies, evaluation rules): "An image that matches the tagging requirements of a rule cannot be expired or archived
  by a rule with a lower priority." 그래서 표식 접두사 규칙을 높은 우선순위에 두면 보호될 것으로 읽힌다. 다만 그 규칙이 개수 기준
  (`imageCountMoreThan` 큰 값)일 때도 "맞는다"로 치는지는 문서가 명시하지 않는다.
- 잘못되면 롤백 대상 이미지가 지워지고 되돌릴 수 없다. 저장소 규칙(파괴적 작업은 계획만)에 따라, 권장 규칙과 확인 절차만 남긴다.
  - 권장: 1) 태그 없음 7일 만료(현행) 2) `deployed-` 접두사, imageCountMoreThan 큰 값(보호) 3) `{env}-` 접두사, 90일 만료
  - 적용 조건: AWS 권한이 있는 사람이 `aws ecr start-lifecycle-policy-preview`로 표식 이미지가 대상에 없음을 확인한 결과를 PR에 남긴다.
- 지금 저장량: 배포마다 이미지가 1개씩 쌓이고 태그 없는 레이어는 7일 뒤 정리된다. 급한 비용 문제는 아니다.

## D-4: admin-api는 core-api와 함께 둔다

- 이슈 판단 그대로. 어드민만 따로 배포해야 할 만큼 자주 바뀌면 그때 분리 기준을 정한다(README "분리 시나리오").
