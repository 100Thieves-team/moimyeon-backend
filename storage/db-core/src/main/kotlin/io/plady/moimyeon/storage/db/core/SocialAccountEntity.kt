package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.SocialLoginProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(name = "social_account")
class SocialAccountEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    val provider: SocialLoginProvider,
    @Column(name = "provider_id", nullable = false, length = 100)
    val providerId: String,
    @Column(name = "linked_email", length = 320)
    val linkedEmail: String?,
) : BaseEntity()
