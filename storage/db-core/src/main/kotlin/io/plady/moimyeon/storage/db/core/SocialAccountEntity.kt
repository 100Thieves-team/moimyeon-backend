package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.SocialLoginProvider
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "social_account")
class SocialAccountEntity(
    @Enumerated(EnumType.STRING)
    val provider: SocialLoginProvider,
    val providerId: String,
    val linkedEmail: String?,
) : BaseEntity()
