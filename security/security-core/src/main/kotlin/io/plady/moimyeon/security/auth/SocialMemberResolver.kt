package io.plady.moimyeon.security.auth

import io.plady.moimyeon.core.enums.SocialLoginProvider

/**
 * 계약: 반환은 로그인할 회원(식별자·권한)이거나 복구 확인이 필요한 탈퇴 회원이다. 이메일 유무/형식 등 도메인 검증은 어댑터(core-api)에서 수행하므로 [email] 은 nullable.
 */
interface SocialMemberResolver {
    fun resolve(provider: SocialLoginProvider, providerId: String, email: String?): SocialLoginResult
}
