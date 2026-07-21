package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "member")
class MemberEntity(
    id: UUID,
    @Column(name = "email", nullable = false, length = 320)
    var email: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: MemberStatus,
    @Column(name = "last_login_at", nullable = false)
    var lastLoginAt: LocalDateTime,
    @Column(name = "withdrawn_at")
    var withdrawnAt: LocalDateTime? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "member_id", nullable = false)
    var socialAccounts: MutableList<SocialAccountEntity> = mutableListOf(),
) : UuidBaseEntity(id)
