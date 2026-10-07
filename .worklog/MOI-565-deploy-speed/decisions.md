# MOI-565 결정 기록

2026-10-06 사용자 승인. MOI-432 DR-001·DR-013과 `docs/knowledge/infra.md` 불변식 일부를 대체한다.

## D-1: dev 배포는 dev push로 시작하고, PR CI 결과로 검증한다

- 결정: dev push CI 성공을 기다리지 않는다. 배포 첫 단계에서 머지 커밋 트리와 같은 트리의 PR head가
  required check `build`를 통과했는지 확인하고, 아니면 실패한다.
- 이유: dev ruleset이 PR·`build`·strict(머지 전 branch update)를 강제하고 bypass actor가 없다.
  머지 결과 트리는 PR CI가 검증한 트리와 같다. dev push CI는 같은 검사의 반복이다(5~7분).
- 대체: DR-001, infra.md "push에 독립 발화하는 배포 워크플로 금지" → "CI가 검증한 트리만 배포".

## D-2: Terraform Apply는 Terraform 구성이 바뀐 커밋에서만 실행한다

- 결정: 배포는 같은 SHA의 Terraform Apply를 기다리지 않는다. 마지막 성공 apply 이후
  `infra/terraform/**` 변경이 없을 때만 바로 진행하고, 있으면 그 apply를 기다린다.
  배포 설정값은 Terraform 실행 artifact 대신 Terraform이 관리하는 비민감 SSM 파라미터에서 읽는다.
- 이유: 앱 커밋마다 no-op plan·apply·변수 동기화로 9분을 쓴다. 누락 방지 의도는 "마지막 적용 이후
  인프라 변경 없음" 확인과 주기 드리프트 plan으로 유지한다.
- 대체: DR-013의 "매 CI 성공마다 no-op plan", "같은 SHA Terraform Apply 성공 확인".
- 리뷰 반영(qa-reviewer): 배포가 `deploy-aws-dev` lock을 잡고 적용 기록을 기다리면, 기록을 쓰는
  apply·sync도 같은 lock이 필요해 서로 막힌다. 대기는 lock 밖 `prepare` job에서 하고 lock 안에서는
  확인만 한다. 적용 기록은 SSM을 먼저, GitHub 변수를 마지막에 써서 부분 실패가 Terraform 재실행
  쪽으로만 기울게 한다.

## D-3: 이미지는 PR CI에서 빌드해 ECR에 올리고, 배포는 트리 이름표로 찾는다

- 결정: 내부 PR CI가 API·Worker 이미지를 빌드해 `tree-<트리>-run-<실행>-<시도>` 태그로 candidate
  ECR에 push한다. 배포는 검증한 `build`와 같은 실행 시도의 `image` job이 성공했을 때만 그 digest를
  복사하고, 아니면 직접 빌드한다. fork·Dependabot PR은 push하지 않는다.
- 이유: 배포 경로에서 빌드(4.7분)를 없앤다. build once 원칙과 맞다.
- 리뷰 반영(qa-reviewer): 트리 태그만으로 고르면 쓰기 권한자가 다른 PR의 트리로 이미지를 먼저 올려
  dev·live로 승격시킬 수 있었다. 태그를 CI 실행에 묶고, 태그는 덮어쓸 수 없으므로 선점되면 해당 image
  job이 실패해 후보가 버려진다.

## D-4: dev ECS에 EC2 1대를 상시 여유로 둔다

- 결정: API 롤링 교체 시 새 인스턴스 기동(약 4분)을 기다리지 않도록 여유 용량을 둔다.
- 이유: dev API 태스크(2 vCPU·1600MB)가 t3.small 한 대를 통째로 쓴다. 비용 증가는 사용자 승인.

## D-5: PR은 Terraform(먼저 머지)과 워크플로 두 개로 나눈다

- 이유: Terraform PR은 plan 판독이 필요하고 머지가 곧 apply 승인이다. 워크플로 PR은 그 IAM·SSM에 의존한다.

## D-6: 이미지에 JDK AOT 캐시를 넣는다 (PR-B)

- 결정: 각 이미지 빌드 중 local 프로필(H2 메모리 DB, 이미 운영 jar에 포함)로 한 번 기동해
  `-XX:AOTCacheOutput`으로 캐시를 만들고, 실행 시 `-XX:AOTCache`로 읽는다. 사용자 승인(2026-10-06).
- 근거(로컬 arm64, 2 CPU·1600MB, 컨텍스트 준비 후 종료, 3회 평균): API 5.7초 → 3.6초, Worker 4.1초 → 2.7초.
  `-XX:AOTMode=on`으로 운영과 같은 클래스패스에서 캐시가 열리는 것을 확인했다.
- 비용: 캐시 API 177MB·Worker 161MB(gzip 약 41MB)가 매 배포 받는 층에 추가되고, PR CI 이미지 빌드가
  이미지당 약 10~15초 늘어난다. dev 프로필에서만 쓰는 클래스(MySQL 드라이버 등)는 캐시에 없다.

