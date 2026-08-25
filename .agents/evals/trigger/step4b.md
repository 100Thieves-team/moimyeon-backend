# Step 4b 트리거 세트 — prompt-change · infra-change · incident-response

저빈도 워크플로우라 축소 세트(양성 3·음성 3·경계 2 = 8행)를 쓴다 (DR-023,
DR-017 경량 프로토콜의 추가 축소).

- prompt-change 음성: n1 구현(req-impl), n2 API 필드(api-spec 계열),
  n3 요금 질문(스킬 없음). 경계: b1 오출력 보고(incident와 3자 경계 —
  incident 진단 후 prompt-change 재투입이 정답 경로), b2 오타
  수정(스킬 규칙상 eval 비교 필요 — 호출이 정답).
- infra-change 음성: n1 로컬 도커 실호출(api-connection 검증 단계),
  n2 구현, n3 장애(incident-response). 경계: b1 배포 시간 단축(설계
  논의 — 질문 또는 진입 모두 합리), b2 plan 판독만(부분 작업).
- incident-response 음성: n1 느린 쿼리 단건(db-reviewer 단독),
  n2 구현, n3 커밋 요약(하네스 무관 소재 — 교훈 7). 경계: b1 LLM
  오출력(incident 진단 → prompt-change 재투입 경로), b2 배포
  실패(CI 실패면 incident 아님 — 확인 질문이 합리).
