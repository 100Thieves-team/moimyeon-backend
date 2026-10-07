# 이슈 키 없는 작업: Bedrock Claude 3.5 Sonnet In-Region 이력서 요약 전환

## 요구사항 요약

- 사용자 요청: 현재 AWS Bedrock 기반 이력서 요약의 Claude Sonnet 5를
  서울 In-Region을 지원하는 Claude 3.5 Sonnet으로 교체한다.
- OpenAI API로 공급자를 교체하거나 OpenAI API 키를 추가하지 않는다.
- 현재 업로드·재시도·`PROCESSING | DONE | FAILED` API 계약은 유지한다.

## 확인한 외부 사실

- AWS Bedrock 서울 리전 카탈로그에 Claude 3.5 Sonnet v1이 ACTIVE,
  ON_DEMAND로 노출된다.
- `bedrock-runtime`의 정확한 In-Region 호출 ID는
  `anthropic.claude-3-5-sonnet-20240620-v1:0`다.
- Claude 3.5 Sonnet은 Converse API와 Anthropic PDF document 입력 경로를
  사용할 수 있다.
- 접두사 없는 direct model ID 호출은 서울 리전 안에서 처리된다.
- 현재 AWS 계정의 모델 상태:
  - authorization: `AUTHORIZED`
  - entitlement: `AVAILABLE`
  - region: `AVAILABLE`
  - agreement: `AVAILABLE`
- AWS 지역별 모델 가용성:
  https://docs.aws.amazon.com/bedrock/latest/userguide/models-region-compatibility.html

## 현재 코드 상태

- `ResumeService.register`는 파일 저장·DB 등록 뒤 동기적으로
  `ResumeSummaryGenerator.generate`를 호출한다.
- `BedrockResumeSummaryGenerator`는 4.5MB 이하 PDF를 Converse document
  block으로 보내고, 그보다 크면 PDFBox로 텍스트를 추출해 보낸다.
- `bedrock-client.yml`의 기본 모델은
  `global.anthropic.claude-sonnet-5`이며 호출 timeout은 30초다.
- Terraform의 ECS task role은 Sonnet 5 foundation model과 global inference
  profile에만 `bedrock:InvokeModel`을 허용한다.
- ECS task role은 현재 Sonnet 5만 허용하므로 Claude 3.5 Sonnet의 서울 리전
  foundation-model ARN으로 교체해야 한다.
- 이력서 요약 전용 eval 셋은 없다. 기존 테스트는 ChatModel 성공·공백 응답·
  예상 예외 변환과 서비스 상태 전이를 검증하지만 모델 출력 품질은 측정하지
  않는다.

## 관련 코드 위치

- `clients/bedrock-client/src/main/kotlin/io/plady/moimyeon/client/bedrock/BedrockResumeSummaryGenerator.kt`:
  PDF 입력·프롬프트·Bedrock 호출 구현
- `clients/bedrock-client/src/main/resources/bedrock-client.yml`:
  모델 ID·timeout·생성 옵션
- `clients/bedrock-client/src/test/kotlin/io/plady/moimyeon/client/bedrock/BedrockResumeSummaryGeneratorTest.kt`:
  Bedrock 어댑터 단위 테스트
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/resume/ResumeService.kt`:
  업로드·재시도와 실패 상태 전환
- `infra/terraform/modules/moimyeon-environment/iam.tf`:
  ECS task의 Bedrock 호출 권한
- `docs/conventions/modules.md`:
  Bedrock 클라이언트와 개인정보 처리 지역성 계약

## 작업 경계

- OpenAI API 직접 호출과 `OPENAI_API_KEY` 도입은 범위 밖이다.
- Resume API·DB 스키마·파일 저장 정책은 바꾸지 않는다.
- AWS 모델 agreement 활성화와 Terraform apply는 사람이 수행한다.
- 실사용자 이력서 원문은 eval 픽스처로 저장하지 않는다.
