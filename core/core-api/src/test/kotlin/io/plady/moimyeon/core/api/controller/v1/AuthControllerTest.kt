package io.plady.moimyeon.core.api.controller.v1

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.plady.moimyeon.core.domain.SessionService
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.JwtTokenProvider
import io.plady.moimyeon.test.api.RestDocsTest
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName
import org.springframework.restdocs.cookies.CookieDocumentation.requestCookies
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.UUID

class AuthControllerTest : RestDocsTest() {
    private lateinit var sessionService: SessionService
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var authCookieFactory: AuthCookieFactory

    @BeforeEach
    fun setUp() {
        sessionService = mockk()
        jwtTokenProvider = mockk()
        authCookieFactory = mockk()
        mockMvc = mockController(AuthController(sessionService, jwtTokenProvider, authCookieFactory))
    }

    @Test
    fun authRefresh() {
        val memberId = UUID.randomUUID()
        every { sessionService.refreshAccess("refresh-credential") } returns memberId
        every { jwtTokenProvider.issue(memberId) } returns "issued-access-token"
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
                    "액세스 토큰 재발급",
                    "REFRESH_TOKEN 쿠키의 세션 크리덴셜을 검증해 새 액세스 토큰을 ACCESS_TOKEN 쿠키(Set-Cookie)로 재발급한다.",
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
    fun authLogout() {
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
}
