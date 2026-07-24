package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.SocialAccountEntity

object MemberMapper {
    fun toDomain(entity: MemberEntity): Member = Member(
        id = entity.id,
        email = Email(entity.email),
        status = entity.status,
        socialAccounts = entity.socialAccounts.map { SocialAccount(it.provider, it.providerId, it.linkedEmail?.let(::Email)) },
        lastLoginAt = entity.lastLoginAt,
        withdrawnAt = entity.withdrawnAt,
    )

    fun toEntity(member: Member): MemberEntity = MemberEntity(
        id = member.id,
        email = member.email.value,
        status = member.status,
        lastLoginAt = member.lastLoginAt,
        withdrawnAt = member.withdrawnAt,
        socialAccounts = member.socialAccounts
            .map { SocialAccountEntity(it.provider, it.providerId, it.linkedEmail?.value) }
            .toMutableList(),
    )
}
