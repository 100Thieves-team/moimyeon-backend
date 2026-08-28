# MOI-484 `$ref` 정규화 결정

## 원인

OpenAPI generator가 만든 local component 이름은 스키마의 정체성이 아니라 생성·등록 순서의 산물이다.
기존 비교기는 local `$ref`를 `$resolved`로 확장하면서도 원래 `$ref` 문자열을 남겨, 내용이 같은 component rename을 계약 변경으로 판정했다.

## 결정

1. local `$ref`의 원래 이름은 fingerprint에서 제외한다.
2. 해석한 실제 schema·response·parameter 내용은 기존처럼 재귀 확장해 비교한다.
3. 순환 참조는 component 이름 대신 expansion stack에서 가리키는 조상의 위치(`$cycleDepth`)로 보존한다.
4. 이름 외 계약이 없는 pure local-ref alias chain은 실제 target까지 접어 표현 차이를 제거한다.
5. pure alias chain 자체가 순환하면 유효한 계약을 만들 수 없으므로 비교를 실패시킨다.
6. 외부 `$ref`는 현재 문서만으로 해석할 수 없으므로 참조 문자열을 그대로 비교한다.
7. local reference의 형제 필드는 기존 정책대로 별도 계약 데이터로 유지한다.

## 실패 보존

- 존재하지 않는 local `$ref`: 비교 실패로 workflow를 중단하고 잘못된 Slack 알림을 보내지 않는다.
- component rename: 변경 없음으로 처리한다.
- pure local-ref alias 추가·rename: 변경 없음으로 처리한다.
- alias의 계약 sibling 변경: `CHANGED`, 문서 전용 sibling과 alias 이름 변경: 무변경으로 처리한다.
- pure local-ref alias cycle: 비교 실패로 처리한다.
- 실제 확장 내용 또는 재귀 topology 변경: 해당 operation을 `CHANGED`로 처리한다.

## 확인 결과

- synthetic rename·recursive rename 회귀 테스트 통과
- pure alias·recursive alias·alias cycle 테스트 통과
- recursive target 변화 양성 테스트 통과
- 실제 오탐 스펙 재계산 결과 `64건 → 1건`
