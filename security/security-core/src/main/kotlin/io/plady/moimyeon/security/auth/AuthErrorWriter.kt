package io.plady.moimyeon.security.auth

import jakarta.servlet.http.HttpServletResponse

/**
 * 필터 레벨(인증/인가 실패)에서 공통 응답 포맷(ApiResponse)을 쓰기 위한 인터페이스.
 *
 * ApiResponse/CoreApiErrorType/ObjectMapper 는 core-api 소유라 security-core 필터에서 직접 못 쓴다.
 * 구현은 core-api 가 제공한다
 */
interface AuthErrorWriter {
    fun writeUnauthorized(response: HttpServletResponse) // 401

    fun writeForbidden(response: HttpServletResponse) // 403
}
