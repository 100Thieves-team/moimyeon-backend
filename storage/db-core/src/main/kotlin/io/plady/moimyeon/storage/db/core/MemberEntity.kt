package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "member",
    uniqueConstraints = [UniqueConstraint(name = "uk_member_nickname", columnNames = ["nickname"])],
)
class MemberEntity(
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    val id: UUID,
    var email: String,
    var nickname: String,
    @Enumerated(EnumType.STRING)
    var status: MemberStatus,
    var lastLoginAt: LocalDateTime,
    var withdrawnAt: LocalDateTime? = null,
    // 소셜 계정은 회원과 라이프사이클이 정확히 같아 예외적으로 연관관계를 걸었음
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "member_id", nullable = false)
    var socialAccounts: MutableList<SocialAccountEntity> = mutableListOf(),
) {
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN

    fun loggedIn(time: LocalDateTime) {
        this.lastLoginAt = time
    }
}
