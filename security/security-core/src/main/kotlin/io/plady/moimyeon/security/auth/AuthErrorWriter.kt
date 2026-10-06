package io.plady.moimyeon.security.auth

import jakarta.servlet.http.HttpServletResponse

interface AuthErrorWriter {
    fun writeUnauthorized(response: HttpServletResponse)

    fun writeForbidden(response: HttpServletResponse)
}
