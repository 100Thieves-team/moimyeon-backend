# AB 태스크 api-conn-01

api-connection은 "스펙 + 구현된 Service 존재"가 전제라 완전한 인라인 재현이
어렵다. 실행 시점에 레포에서 모킹 상태인 엔드포인트 하나를 선정해 대상으로
쓴다 (선정한 대상을 결과에 기록 — 재현 조건).

## 프롬프트 (With/Without 동일, {endpoint}는 실행 시 치환)

> MOI-EVAL-03으로 간주하고, {endpoint} 모킹을 걷어내고 구현된 Service에
> 연결해줘.

## 판정 기준

기계 판정:

- [ ] A1. 모킹 스텁 제거 + 실구현 배선, URI·응답 계약 불변
- [ ] A2. RestDocs 테스트가 실구현 기준으로 교체되고 통과
- [ ] A3. `./gradlew test ktlintCheck` 통과
- [ ] A4. (With만) 도커 기동 후 curl 실호출 기록이 worklog에 남음

사람 판정:

- [ ] H1. Facade 도입 판단이 layers.md 기준(여러 Service 조합 시만)을 따름
- [ ] H2. 전제 미충족 시(Service 없음) 추측 구현하지 않고 정지했는가
