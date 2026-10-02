package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.session.SessionService
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.core.support.response.ApiResponse
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.JwtTokenProvider
import io.plady.moimyeon.security.auth.RestoreTokenProvider
import io.plady.moimyeon.security.auth.SessionIssuer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val sessionService: SessionService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authCookieFactory: AuthCookieFactory,
    private val memberFinder: MemberFinder,
    private val memberService: MemberService,
    private val restoreTokenProvider: RestoreTokenProvider,
    private val sessionIssuer: SessionIssuer,
) {
    @PostMapping("/v1/auth/refresh")
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<Any> {
        val refreshToken = authCookieFactory.resolveRefresh(request)
        val credential = requireFound(refreshToken, CoreErrorType.INVALID_SESSION)
        val memberId = sessionService.authenticate(credential)
        val member = memberFinder.getById(memberId)
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            authCookieFactory.createAccess(jwtTokenProvider.issue(member.id, member.role)).toString(),
        )
        return ApiResponse.success()
    }

    // 복구 커밋과 세션 저장은 별도 트랜잭션이다. 세션 저장이 실패하면 복구된 채 로그인만 안 된 상태로 남지만,
    // 다음 로그인이 일반 회원 경로로 흘러 회복되므로 원자성을 요구하지 않는다(로그인 성공 처리와 같은 판단).
    @PostMapping("/v1/auth/restoration")
    fun restore(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<Any> {
        // 상태를 바꾸는 쿠키 인증 요청이라 다른 사이트가 폼·단순 요청으로 대신 보내지 못하게 JSON 요청만 받는다.
        // JSON 은 사이트를 넘으면 CORS 사전 요청을 거쳐 허용한 오리진만 통과한다.
        if (!isJsonRequest(request)) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        val restoreToken = requireFound(authCookieFactory.resolveRestore(request), CoreErrorType.RESTORATION_EXPIRED)
        val claim = requireFound(restoreTokenProvider.resolve(restoreToken), CoreErrorType.RESTORATION_EXPIRED)
        val memberId = claim.memberId
        memberService.restore(memberId, claim.issuedAt)
        val member = memberService.getMember(memberId)
        // 쿠키를 모두 만든 뒤에 내려야 세션 발급이 실패한 에러 응답에 액세스 쿠키만 실리지 않는다.
        val cookies = listOf(
            authCookieFactory.createAccess(jwtTokenProvider.issue(member.id, member.role)),
            authCookieFactory.createRefresh(sessionIssuer.open(memberId)),
            authCookieFactory.expireRestore(),
        )
        cookies.forEach { response.addHeader(HttpHeaders.SET_COOKIE, it.toString()) }
        return ApiResponse.success()
    }

    @PostMapping("/v1/auth/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<Any> {
        val refreshToken = authCookieFactory.resolveRefresh(request)
        refreshToken?.let { sessionService.logout(it) }
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.expireAccess().toString())
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.expireRefresh().toString())
        return ApiResponse.success()
    }

    private fun isJsonRequest(request: HttpServletRequest): Boolean = runCatching {
        MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.contentType))
    }.getOrDefault(false)
}
