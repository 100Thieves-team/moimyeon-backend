package io.plady.moimyeon.support.logging

// 예외 메시지가 정적 문구임을 선언하는 마커. 로그 formatter는 이 타입의 예외만 메시지를 남기고
// 그 외 예외(프레임워크·드라이버·마커 없는 앱 예외)는 타입과 코드 위치만 남긴다.
// 구현 타입은 메시지에 사용자 입력·DB 값·외부 응답 원문을 보간하지 않아야 한다.
interface SafeLogMessage
