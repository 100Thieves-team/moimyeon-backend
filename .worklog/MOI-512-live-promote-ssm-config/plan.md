# MOI-512 live 승격 원본 틀을 SSM 배포 설정에서 읽기

Linear: MOI-512(live 승격 준비 검증). 제품 명세와 무관한 인프라 작업이라 Wiki는 조회하지 않았다.

## 배경

2026-10-09 #183 릴리스의 첫 Promote Live가 실패했다. Terraform Apply가 live 원본 틀을
교체(`:2` → `:6`)하고 GitHub 변수를 갱신한 뒤 promote job이 시작했지만, workflow 실행 시작 때
고정된 변수 값(`:2`, 비활성)을 읽어 `deregisteredAt` 입력 오류로 멈췄다. 재실행으로 끝났다.
같은 사고가 2026-09-11 dev에서 있었고, dev는 MOI-565에서 SSM 배포 설정을 읽도록 바뀌었다.

## 변경

1. Terraform: `publish_deploy_config` 변수 추가. live가 `/moimyeon/live/deploy/config`를
   게시한다(후보 저장소 없음). live 배포 role에 그 파라미터 읽기만 추가한다.
2. `deploy_config.py`: `--environment dev|live`. live는 후보 저장소가 없어야 하고 원본 틀은
   `moimyeon-live-*` 리비전이어야 한다.
3. `promote-live.yml`: Terraform 경계 뒤 promote job에서 SSM 설정을 읽어 원본 틀로 쓴다.
   파라미터 이름은 workflow에 고정한다(새 GitHub 변수로 넘기면 첫 릴리스에서 같은 문제).
   설정의 클러스터·서비스·컨테이너·저장소·IMAGE_URI 파라미터·app_url·bundle prefix가 GitHub 변수와
   같은지 확인한다(다르면 실행 시작 뒤 Terraform이 바꾼 것이므로 멈추고 재실행).
4. `deploy-ecs-image.sh`: 원본 틀이 ACTIVE가 아니면 등록 전에 이유를 밝히고 멈춘다. 이 스크립트의 원본 틀 경로는
   live 승격만 쓴다(dev 배포는 `prepare_ecs_task.py`가 이미 ACTIVE를 검사한다).
5. 테스트: live 설정 검증, 비활성 원본 틀 차단과 승격 workflow 계약(`deploy-ecs-template-test.sh`).

## 영향

- live plan 예상: `aws_ssm_parameter.deploy_config[0]` 생성, 배포 role 정책 갱신. 교체 없음.
- dev plan 예상: 변경 없음(정책 문서의 표현만 바뀌고 결과는 같아야 한다).
- 이 PR의 릴리스에서는 Terraform이 파라미터와 권한을 먼저 만들고, promote는 Terraform 경계 뒤에 읽는다.

## 릴리스 순서

Promote Live는 workflow_run이라 dev의 YAML과 main_sha의 스크립트를 함께 쓴다. 이 PR이 dev에 머지된 뒤의
첫 승격은 이 PR을 포함한 릴리스여야 한다. 그 전 소스를 승격하면 설정 읽기 단계에서 AWS 변경 전에 멈춘다.

## 체크포인트

- [x] 변경·로컬 검증
- [ ] CI plan 판독·승인(머지 = apply 승인)
- [ ] PR
