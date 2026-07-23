package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import jakarta.persistence.CascadeType
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
    var email: String,
    @Enumerated(EnumType.STRING)
    var status: MemberStatus,
    var lastLoginAt: LocalDateTime,
    var withdrawnAt: LocalDateTime? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "member_id", nullable = false)
    var socialAccounts: MutableList<SocialAccountEntity> = mutableListOf(),
) : UuidBaseEntity(id) {
    fun loggedIn(time: LocalDateTime) {
        this.lastLoginAt = time
    }
}
