# MOI-527 계획

## 변경

1. `router/v2/` 추가. `fluent-bit.conf.tftpl`은 v1과 같고, `sanitize.lua`만
   message와 예외 message를 복사한다. v1은 이전 task definition이 읽으므로 그대로 둔다.
2. `main.tf`: `revisions = ["v1", "v2"]`, `active_revision = "v2"`.
3. 길이 상한: message·예외 message 각각 16384바이트. 넘으면 UTF-8 글자 경계에서
   자르고 `…`를 붙인다(폐기하지 않음). 앱 상한 4096자가 한글·이모지로 최대
   약 16KB라 정상 앱 출력은 자르지 않고, 앱을 우회한 비정상 입력만 막는다.
4. 16KiB 넘는 줄은 Docker가 조각으로 나누므로 파싱 전에 multiline(partial_message)으로 합친다(QA 리뷰 반영).
5. smoke: message 전달, 예외 message 전달, 라우터 예약 필드(service·environment·
   category) 덮어쓰기 불가, 상한 초과 시 자름을 검사한다. 비허용 필드 차단은 유지.
6. 모듈 tftest에 v2 설정 객체와 활성 revision 검사를 추가한다.
7. README의 안전한 출력·배포 상태 설명을 갱신한다.

## 영향

- dev·live 모두 수집기 설정 객체(v2) 생성과 API·Worker task definition 교체가
  예상된다. 다음 배포(dev push, live 승격)부터 새 설정이 적용된다.
- S3 ops 보관본(90일)에도 메시지가 남는다. 개인정보 방어는 앱의 마스킹과 호출
  지점 규칙이 맡는다(operations.md 2026-09-21 레슨).

## 체크포인트

- [x] 변경·로컬 검증
- [ ] CI plan 판독·승인(머지 = apply 승인)
- [ ] PR
