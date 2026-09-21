package io.plady.moimyeon.support.logging

// 앱 패키지(io.plady.*) 예외의 메시지 보존 규칙을 검증하기 위한 테스트 전용 예외.
class ProbeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
