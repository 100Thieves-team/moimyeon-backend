package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.SocialLoginProvider
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

// 회원과 함께 삭제되므로 삭제 시각은 member.deleted_at 이 대신한다.
// (provider, provider_id) 유니크는 탈퇴자 재가입 차단에 쓰여 삭제 후에도 키를 점유해야 한다.
@Entity
@Table(
    name = "social_account",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_social_account_provider_provider_id", columnNames = ["provider", "provider_id"]),
    ],
)
class SocialAccountEntity(
    @Enumerated(EnumType.STRING)
    val provider: SocialLoginProvider,
    val providerId: String,
    val linkedEmail: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN
}
