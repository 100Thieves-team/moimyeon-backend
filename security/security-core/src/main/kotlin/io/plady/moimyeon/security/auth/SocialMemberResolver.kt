package io.plady.moimyeon.security.auth

import io.plady.moimyeon.core.enums.SocialLoginProvider
import java.util.UUID

/**
 * 인증(security) 계층이 "소셜 신원 → 우리 회원 확정(provisioning 포함)"을 도메인에 요청하기 위한 인터페이스
 *
 * 구현(어댑터)은 상위 모듈인 core-api 가 제공한다. 이렇게 의존을 역전시켜, spring-security 가 core-api 로 새지 않으면서도 성공 핸들러가 도메인을 호출한다.
 *
 * 계약: 반환은 내부 memberId(UUID). 이메일 유무/형식 등 도메인 검증은 어댑터(core-api)에서 수행하므로 [email] 은 nullable.
 */
interface SocialMemberResolver {
    fun resolve(provider: SocialLoginProvider, providerId: String, email: String?): UUID
}
