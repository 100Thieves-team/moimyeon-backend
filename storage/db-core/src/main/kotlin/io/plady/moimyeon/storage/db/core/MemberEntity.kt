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
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

// 라이프사이클을 도메인 상태(MemberStatus)로 스스로 관리하므로 소프트 삭제 베이스를 상속하지 않는다.
@Entity
@Table(name = "member")
class MemberEntity(
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    val id: UUID,
    var email: String,
    @Enumerated(EnumType.STRING)
    var status: MemberStatus,
    var lastLoginAt: LocalDateTime,
    var withdrawnAt: LocalDateTime? = null,
    // 소셜 계정은 회원과 라이프사이클이 정확히 같아(함께 생성·삭제) 예외적으로 연관관계를 건다.
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
