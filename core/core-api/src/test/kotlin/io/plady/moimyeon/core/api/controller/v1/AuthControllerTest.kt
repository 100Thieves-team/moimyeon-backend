package io.plady.moimyeon.core.api.controller.v1

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.session.SessionService
import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.IssuedSession
import io.plady.moimyeon.security.auth.JwtTokenProvider
import io.plady.moimyeon.security.auth.RestoreClaim
import io.plady.moimyeon.security.auth.RestoreTokenProvider
import io.plady.moimyeon.security.auth.SessionIssuer
import io.plady.moimyeon.test.api.RestDocsTest
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName
import org.springframework.restdocs.cookies.CookieDocumentation.requestCookies
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class AuthControllerTest : RestDocsTest() {
    private lateinit var sessionService: SessionService
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var authCookieFactory: AuthCookieFactory
    private lateinit var memberFinder: MemberFinder
    private lateinit var memberService: MemberService
    private lateinit var restoreTokenProvider: RestoreTokenProvider
    private lateinit var sessionIssuer: SessionIssuer

    private val authRefreshSummary = "액세스 토큰 재발급"
    private val authRefreshDescription =
        "REFRESH_TOKEN 쿠키의 세션 크리덴셜을 검증해 새 액세스 토큰을 ACCESS_TOKEN 쿠키(Set-Cookie)로 재발급한다. " +
            "쿠키가 없거나 세션이 만료·폐기됐으면 401(E1104)로 응답하며, FE 는 재로그인으로 보낸다."

    private val authRestoreSummary = "탈퇴 계정 복구"
    private val authRestoreDescription =
        "탈퇴한 회원이 같은 Google 계정으로 로그인하면 세션 대신 RESTORE_TOKEN 쿠키(10분)를 받고 프론트 복구 확인 화면으로 이동한다. " +
            "사용자가 복구를 확인하면 이 API 를 호출한다. 계정·프로필·보관 이력서가 돌아오고 일반 로그인과 같은 " +
            "ACCESS_TOKEN·REFRESH_TOKEN 쿠키를 발급하며 RESTORE_TOKEN 쿠키는 만료시킨다. 나간 룸·철회된 신청·끝난 세션은 " +
            "돌아오지 않고, 이용 제한 상태는 그대로다(「회원 및 프로필」 R174~R178). " +
            "쿠키가 없거나 만료·위조됐으면 401(E1105)로 응답하며 FE 는 다시 로그인으로 보낸다. 이미 복구된 회원이면 다시 로그인만 된다(멱등). " +
            "다른 사이트가 대신 보내지 못하도록 Content-Type: application/json 요청만 받는다(아니면 400 E400). " +
            "토큰을 발급한 뒤 다시 탈퇴했다면 그 토큰은 쓸 수 없다(401 E1105). 토큰의 회원이 없으면 404(E1006)."

    @BeforeEach
    fun setUp() {
        sessionService = mockk()
        jwtTokenProvider = mockk()
        authCookieFactory = mockk()
        memberFinder = mockk()
        memberService = mockk()
        restoreTokenProvider = mockk()
        sessionIssuer = mockk()
        every { authCookieFactory.resolveRefresh(any()) } returns null
        every { authCookieFactory.resolveRestore(any()) } returns null
        mockMvc = mockController(
            AuthController(sessionService, jwtTokenProvider, authCookieFactory, memberFinder, memberService, restoreTokenProvider, sessionIssuer),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun authRefresh() {
        val memberId = UUID.randomUUID()
        val member = mockk<Member>()
        every { sessionService.authenticate("refresh-credential") } returns memberId
        every { memberFinder.getById(memberId) } returns member
        every { member.id } returns memberId
        every { member.role } returns MemberRole.USER
        every { jwtTokenProvider.issue(memberId, MemberRole.USER) } returns "issued-access-token"
        every { authCookieFactory.resolveRefresh(any()) } returns "refresh-credential"
        every { authCookieFactory.createAccess("issued-access-token") } returns
            ResponseCookie.from(AuthCookieFactory.ACCESS_TOKEN, "issued-access-token")
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .build()

        mockMvc.perform(
            post("/v1/auth/refresh")
                .cookie(Cookie(AuthCookieFactory.REFRESH_TOKEN, "refresh-credential")),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "authRefresh",
                    authRefreshSummary,
                    authRefreshDescription,
                    requestCookies(
                        cookieWithName(AuthCookieFactory.REFRESH_TOKEN).description("세션 리프레시 크리덴셜"),
                    ),
                    responseHeaders(
                        headerWithName(HttpHeaders.SET_COOKIE).description("재발급된 ACCESS_TOKEN 쿠키"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `authRefresh 세션 무효 E1104`() {
        mockMvc.perform(post("/v1/auth/refresh"))
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("authRefresh-e1104", authRefreshSummary, authRefreshDescription, errorResponseFields()))
    }

    @Test
    fun authLogout() {
        every { authCookieFactory.resolveRefresh(any()) } returns "refresh-credential"
        every { sessionService.logout("refresh-credential") } just Runs
        every { authCookieFactory.expireAccess() } returns
            ResponseCookie.from(AuthCookieFactory.ACCESS_TOKEN, "").path("/").maxAge(0).build()
        every { authCookieFactory.expireRefresh() } returns
            ResponseCookie.from(AuthCookieFactory.REFRESH_TOKEN, "").path(AuthCookieFactory.REFRESH_PATH).maxAge(0).build()

        mockMvc.perform(
            post("/v1/auth/logout")
                .cookie(Cookie(AuthCookieFactory.REFRESH_TOKEN, "refresh-credential")),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "authLogout",
                    "로그아웃",
                    "REFRESH_TOKEN 쿠키의 세션을 폐기하고 ACCESS_TOKEN·REFRESH_TOKEN 쿠키를 만료(Set-Cookie)시킨다. 쿠키가 없어도 성공으로 응답한다.",
                    requestCookies(
                        cookieWithName(AuthCookieFactory.REFRESH_TOKEN).optional().description("세션 리프레시 크리덴셜 (없으면 쿠키 만료만 수행)"),
                    ),
                    responseHeaders(
                        headerWithName(HttpHeaders.SET_COOKIE).description("만료 처리된 ACCESS_TOKEN·REFRESH_TOKEN 쿠키"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun authRestore() {
        val memberId = UUID.randomUUID()
        val member = mockk<Member>()
        val session = IssuedSession("refresh-credential", LocalDateTime.of(2026, 10, 16, 12, 0))
        every { authCookieFactory.resolveRestore(any()) } returns "restore-token"
        every { restoreTokenProvider.resolve("restore-token") } returns RestoreClaim(memberId, Instant.parse("2026-10-02T03:00:00Z"))
        every { memberService.restore(memberId, any()) } just Runs
        every { memberService.getMember(memberId) } returns member
        every { member.id } returns memberId
        every { member.role } returns MemberRole.USER
        every { sessionIssuer.open(memberId) } returns session
        every { jwtTokenProvider.issue(memberId, MemberRole.USER) } returns "issued-access-token"
        every { authCookieFactory.createAccess("issued-access-token") } returns
            ResponseCookie.from(AuthCookieFactory.ACCESS_TOKEN, "issued-access-token").path("/").build()
        every { authCookieFactory.createRefresh(session) } returns
            ResponseCookie.from(AuthCookieFactory.REFRESH_TOKEN, "refresh-credential").path(AuthCookieFactory.REFRESH_PATH).build()
        every { authCookieFactory.expireRestore() } returns
            ResponseCookie.from(AuthCookieFactory.RESTORE_TOKEN, "").path(AuthCookieFactory.REFRESH_PATH).maxAge(0).build()

        mockMvc.perform(
            post("/v1/auth/restoration")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(Cookie(AuthCookieFactory.RESTORE_TOKEN, "restore-token")),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "authRestore",
                    authRestoreSummary,
                    authRestoreDescription,
                    requestCookies(
                        cookieWithName(AuthCookieFactory.RESTORE_TOKEN).description("로그인 성공 처리가 심은 복구 확인 토큰 (10분)"),
                    ),
                    responseHeaders(
                        headerWithName(HttpHeaders.SET_COOKIE)
                            .description("새 ACCESS_TOKEN·REFRESH_TOKEN 쿠키와 만료 처리된 RESTORE_TOKEN 쿠키"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `authRestore 복구 확인 쿠키 없음 E1105`() {
        mockMvc.perform(post("/v1/auth/restoration").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("authRestore-e1105", authRestoreSummary, authRestoreDescription, errorResponseFields()))
    }

    @Test
    fun `authRestore JSON 요청이 아님 E400`() {
        mockMvc.perform(
            post("/v1/auth/restoration")
                .cookie(Cookie(AuthCookieFactory.RESTORE_TOKEN, "restore-token")),
        )
            .andExpect(status().isBadRequest)
            .andDo(documentApi("authRestore-e400", authRestoreSummary, authRestoreDescription, errorResponseFields()))
    }

    @Test
    fun `authRestore 회원 없음 E1006`() {
        val memberId = UUID.randomUUID()
        every { authCookieFactory.resolveRestore(any()) } returns "restore-token"
        every { restoreTokenProvider.resolve("restore-token") } returns RestoreClaim(memberId, Instant.parse("2026-10-02T03:00:00Z"))
        every { memberService.restore(memberId, any()) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        mockMvc.perform(
            post("/v1/auth/restoration")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(Cookie(AuthCookieFactory.RESTORE_TOKEN, "restore-token")),
        )
            .andExpect(status().isNotFound)
            .andDo(documentApi("authRestore-e1006", authRestoreSummary, authRestoreDescription, errorResponseFields()))
    }

    @Test
    fun `복구 중 세션 발급이 실패하면 어떤 인증 쿠키도 내려보내지 않는다`() {
        val memberId = UUID.randomUUID()
        val member = mockk<Member>()
        every { authCookieFactory.resolveRestore(any()) } returns "restore-token"
        every { restoreTokenProvider.resolve("restore-token") } returns RestoreClaim(memberId, Instant.parse("2026-10-02T03:00:00Z"))
        every { memberService.restore(memberId, any()) } just Runs
        every { memberService.getMember(memberId) } returns member
        every { member.id } returns memberId
        every { member.role } returns MemberRole.USER
        every { jwtTokenProvider.issue(memberId, MemberRole.USER) } returns "issued-access-token"
        every { authCookieFactory.createAccess("issued-access-token") } returns
            ResponseCookie.from(AuthCookieFactory.ACCESS_TOKEN, "issued-access-token").path("/").build()
        every { sessionIssuer.open(memberId) } throws IllegalStateException("session store down")

        mockMvc.perform(
            post("/v1/auth/restoration")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(Cookie(AuthCookieFactory.RESTORE_TOKEN, "restore-token")),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
    }
}
