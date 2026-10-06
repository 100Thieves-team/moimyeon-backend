# MOI-565 미결정 사항

## [TBD] dev 평상시 EC2 대수 확인

- `envs/dev/main.tf` 주석의 "평소 3대(API·Worker·Redis)"를 근거로 최소 4대로 잡았다.
  AWS 접근이 없어 실제 대수는 확인하지 못했다. 평소 2대라면 빈 인스턴스가 2대가 되어 1대분 비용이 더 든다.
  PR-A plan 판독 시 ASG 현재 대수와 함께 확인한다.

## [TBD] dev 실제 기동 시간

- AOT 캐시는 PR-B에 넣었다(decisions.md D-6). dev(t3.small)의 실제 기동 시간과 단축 폭은 배포 뒤
  CloudWatch `Started ... in N seconds`로 확인한다.

## [확인 필요] Worker 교체 생략은 효과가 없다

- `storage:redis-core`가 `core:core-api`에 의존해 Worker 이미지에 core-api가 들어간다. API 코드만 바꿔도
  Worker 이미지가 바뀌므로 경로 기준 생략을 넣지 않았다. 모듈 의존 정리는 별도 판단이 필요하다.
