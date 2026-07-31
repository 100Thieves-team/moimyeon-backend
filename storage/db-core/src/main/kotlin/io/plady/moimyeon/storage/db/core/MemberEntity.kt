package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "member",
    uniqueConstraints = [UniqueConstraint(name = "uk_member_nickname", columnNames = ["nickname"])],
)
class MemberEntity(
    id: UUID,
    val email: String,
    nickname: String,
    status: MemberStatus,
    lastLoginAt: LocalDateTime,
    socialAccounts: List<SocialAccountEntity> = emptyList(),
) : UuidBaseEntity(id) {
    var nickname: String = nickname
        protected set

    @Enumerated(EnumType.STRING)
    var status: MemberStatus = status
        protected set

    var lastLoginAt: LocalDateTime = lastLoginAt
        protected set

    // 소셜 계정은 회원과 라이프사이클이 정확히 같아 예외적으로 연관관계를 걸었음.
    // cascade=ALL + orphanRemoval 이므로 컬렉션 조작이 곧 INSERT/DELETE 다 — 외부에 노출하지 않는다.
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "member_id", nullable = false)
    private val socialAccounts: MutableList<SocialAccountEntity> = socialAccounts.toMutableList()

    fun socialAccounts(): List<SocialAccountEntity> = socialAccounts.toList()

    fun loggedIn(time: LocalDateTime) {
        this.lastLoginAt = time
    }

    fun changeNickname(nickname: String) {
        this.nickname = nickname
    }
}
