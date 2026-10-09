# MOI-588 이미지 빌드 — 빌드 캐시 복구·이미지별 빌드 대상 분리 — 컨텍스트

## 이슈

- [MOI-588](https://linear.app/100-thieves/issue/MOI-588). 상위 이슈 [MOI-584](https://linear.app/100-thieves/issue/MOI-584)(멀티모듈 배포 개선)의 과제 3.
- 인프라·워크플로 작업이라 Wiki 비대상. 측정값은 상위 이슈 본문에 있다.

### MOI-584 서브이슈 (권장 순서)

| 순서 | 이슈 | 내용 |
| --- | --- | --- |
| 1 | MOI-588 | 이미지 빌드 캐시 복구·이미지별 빌드 대상 분리 (이 작업) |
| 2 | MOI-589 | 일부 서비스만 배포해도 배포 기록·live 승격·롤백 정합 (MOI-590 선행) |
| 2 | MOI-591 | redis-core의 API·Worker 어댑터 분리 (MOI-590 전에 검토) |
| 2 | MOI-590 | Worker 입력이 바뀔 때만 Worker 교체 |
| 3 | MOI-592 | 앱 코드 없는 커밋은 앱 재배포 생략 |
| 3 | MOI-593 | CI 검증 범위 조정·이미지 job 생략 |
| 4 | MOI-594 | Worker 교체 중 소비 공백·준비 상태 확인 |
| 4 | MOI-595 | dev 배포 스크립트 통합·core-batch·ECR 보존 |

## 문제 (코드로 확인함)

- `Dockerfile` builder 단계: `COPY . .` 뒤 `:core:core-api:bootJar :core:core-worker:bootJar`를 한 번에 실행.
  - `/root/.gradle`은 BuildKit cache mount라서 `type=gha` 캐시에 실리지 않는다. GitHub 러너는 매번 새것이라 mount도 비어 있다.
  - `.dockerignore`가 `docs/`·`.worklog/`·`.agents/`·`*.md`를 빼지 않으므로 앱과 무관한 파일만 바뀌어도 `COPY . .` 레이어가 깨진다.
  - Worker 이미지만 필요할 때도 API bootJar까지 만든다.
- `.github/workflows/ci.yml` `image` job(210행): API는 `cache-to: type=gha,mode=max,scope=deployment-images`, Worker는 같은 scope `cache-from`만. 같은 러너의 buildx 빌더로 builder 단계를 공유한다.
- `.github/workflows/deploy-aws.yml` 481행: 후보 이미지가 없을 때 같은 Dockerfile·같은 캐시 설정으로 다시 빌드한다(대체 경로).
- `build` job은 `gradle/actions/setup-gradle`로 러너의 Gradle 캐시를 이어 쓰고 있다(대조군).
- `gradle.properties`에 `org.gradle.caching` 설정 없음(Gradle build cache 미사용).

## 지켜야 할 것

- 앱 jar 이름 정규화(`core-api.jar`·`core-worker.jar` → 추출 전 `app.jar`)는 유지(`docs/knowledge/operations.md` 2026-10-07 이전 항목, 94~95행).
- 후보 이미지는 provenance 기본값(index)으로 빌드해야 digest 보존 승격이 된다(operations.md 2026-10-07).
- 같은 앱 코드면 같은 digest가 나오는 재현 가능한 빌드 성질에 승격이 기대고 있다.
- AOT 캐시 학습 단계(MOI-565)는 이미지마다 유지.
- 배포 계약 테스트 `infra/terraform/tests/deploy-workflow-contract.sh`가 이미지 빌드 단계 이름·순서를 검사한다(103행 등).

## 작업 경계

- Gradle build cache로 테스트 결과를 재사용하는 것, 이미지 job 생략 조건은 MOI-593 소관.
- 배포 대상 판단(어느 서비스를 교체할지)은 MOI-590·592 소관.
- Terraform 변경 없음 예상. 생기면 plan까지만.
