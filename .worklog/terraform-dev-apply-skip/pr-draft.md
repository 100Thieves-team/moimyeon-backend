# fix(infra): shared 변경이 없을 때 dev Terraform 적용 생략 수정

shared에 변경이 없으면 apply-shared가 생략되고 그 상태가 의존 체인을 따라 전파돼,
변경을 찾은 dev plan 뒤에도 apply-dev가 생략됐다. 전체 Terraform Apply는 성공으로
표시되면서 미반영 변경이 매일 drift 알림으로 남았다.

apply-dev에 !cancelled()를 명시하고 select 성공 검사와 기존 plan 성공·최신 커밋·
변경 여부 조건을 함께 유지한다. shared no-op일 때 dev 변경을 적용하면서 취소·실패·
stale plan을 차단한다. apply-dev 블록 한정 회귀 계약도 추가했다.

## 검증

기존 코드에서 새 회귀 계약 실패, 수정 후 통과. 관련 Terraform·배포 계약 6개,
Terraform 1.15.9 fmt 및 dev/shared/live validate 통과. 공식 Actions 조건식 평가기로
13개 시나리오 및 shared 실패/dev no-op sync 확인. `./gradlew test ktlintCheck` 통과. 읽기 전용 QA PASS.

아직 실행하지 않은 검증: PR CI plan 및 실제 GitHub scheduling.

## 배포 영향 및 plan

이번 수정은 AWS 리소스 정의를 바꾸지 않지만, 머지하면 기존 미적용 dev 변경이
자동 apply 대상으로 돌아온다. 9월 7일 진단 plan은 dev 추가 0 / 변경 2 / 삭제 0,
shared 변경 0이었다. 변경 대상은 Bedrock IAM 허용 리소스 제한과 ECS 시작 템플릿의
권장 AMI 갱신이다. 최신 PR plan으로 재확인해야 하며 이 수치는 승인된 PR plan이 아니다.

live 경로·활성화 flag·AWS 권한·AMI 선택 방식은 변경하지 않는다.
머지는 CI 자동 apply를 승인하는 행위이며 직접 apply는 하지 않는다.
