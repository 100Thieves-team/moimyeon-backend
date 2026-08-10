package io.plady.moimyeon.security.auth

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

class MemberRoleJwtGrantedAuthoritiesConverter : Converter<Jwt, Collection<GrantedAuthority>> {
    override fun convert(source: Jwt): Collection<GrantedAuthority> = source
        .getClaimAsStringList(JwtTokenProvider.ROLES_CLAIM)
        .orEmpty()
        .map { SimpleGrantedAuthority("ROLE_$it") }
}
