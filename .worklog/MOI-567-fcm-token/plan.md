# MOI-567 웹 푸시 발송 실패 수정 (하위 MOI-568, MOI-566)

## 단계

- [x] A. 이슈 파악: MOI-567, 하위 MOI-568·MOI-566 (2026-10-05)
- [x] B. 구현·테스트 (`./gradlew test ktlintCheck` 통과)
- [x] C. 명세 대조(wiki-sync): 알림 클릭 경로를 정한 명세 없음, R158과 일치. 갱신 불필요 (wiki-sync.md)
- [x] D-1. 리뷰 게이트: qa-reviewer CONDITIONAL(명세 대조, C로 해소), code-reviewer 필수 없음. 권장 반영(로그 값 공백, 미사용 속성 제거, FCM 결과 로그를 finally로, 테스트 예시 경로)
- [ ] D-2. 커밋·PR (base: dev)

## 접근

- MOI-567: 등록 API가 저장하는 값은 FCM 등록 토큰이다. `FirebaseAdminFcmGateway`가 이를 FID 대상(`addAllFids`)으로
  보내 FCM이 `404 UNREGISTERED`를 돌려줬고, 백엔드는 이를 만료로 보고 등록을 삭제한 뒤 메일로 대체했다.
  `addAllTokens`로 바꾸고 게이트웨이 테스트가 수신 대상 종류를 검증한다.
- MOI-568: 알림 클릭 경로를 프론트 실제 룸 상세 경로 `/interviews/{roomId}`로 바꾼다.
  경로는 `NotificationComposer`의 한 곳에서만 만들며 웹 푸시 링크와 메일 본문이 같은 값을 쓴다.
- MOI-566: worker가 알림마다 남기는 로그가 처리 시작(debug)뿐이라 결과를 추적할 수 없었다.
  - `notification.send.completed`: 채널·정책·기기 수, 웹 푸시 결과(전달·미전달·건너뛴 이유), 메일 결과(발송·꺼짐·불필요)
  - `web-push.fcm.result`: FCM이 기기별로 수락·거절한 건수와 오류 코드
  - `web-push.registration.remove`: 만료 판정 등록 삭제 건수와 회원
  - 수준은 worker 경계 규칙대로 DEBUG(dev에서 켜짐). 등록 토큰·이메일은 남기지 않는다.

## 결정

- FCM 오류 코드를 로그에 남기기 위해 `FcmSendResult`에 `errorCode`를 추가했다(기본 null).
- `ChannelNotificationSender`의 발송 판단은 그대로 두고, 결과를 표현하는 내부 enum만 추가했다.
