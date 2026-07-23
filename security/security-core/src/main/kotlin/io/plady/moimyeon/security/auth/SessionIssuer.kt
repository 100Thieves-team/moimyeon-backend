package io.plady.moimyeon.security.auth

import java.util.UUID

// 로그인 성공 시 세션을 개시하는 인터페이스
interface SessionIssuer {
    fun open(memberId: UUID): IssuedSession
}
