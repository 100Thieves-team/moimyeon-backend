# req-impl-01 판정 기준

기계 판정 (스크립트 가능):

- [ ] A1. service 테스트가 존재하고 `unitTest` 또는 `contextTest` 태그 규칙을
      따른다 (docs/conventions/testing.md)
- [ ] A2. 취소 불가 케이스가 에러 코드와 함께 테스트된다
- [ ] A3. `./gradlew test ktlintCheck` 통과
- [ ] A4. (With만) worklog/MOI-EVAL-01/에 plan.md가 생성되고 체크포인트에서
      정지했다

사람 판정 (rubric):

- [ ] H1. Service 본문이 흐름만 보이는가 — 판정·예외는 도구(Implement)로
      내려갔는가 (layers.md)
- [ ] H2. 시간 판정에 Clock 주입을 썼는가 (테스트 가능성)
- [ ] H3. 결정·모호점이 기록됐는가 (없으면 Without과의 차이 소멸)

스킬의 가치는 A4·H1~H3에서 갈릴 것으로 예상한다 — A1~A3은 Without도 통과할
수 있다. "둘 다 통과하는 항목"은 측정에서 제외하지 말고 그 사실 자체를
기록한다 (스킬 무용 판정의 근거가 되므로).
