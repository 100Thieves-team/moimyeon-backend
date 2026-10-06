package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

// "회원당 프로필 1개"는 uk_member_profile_member 가 지킨다. member_id 를 PK 로 쓰면 충돌이
// PRIMARY 로만 나와 제약명 판별이 안 되고, 그러면 번역을 쓰기 옆에 둘 수 없다.
// 소프트 삭제된 프로필도 이 유니크를 점유한다 — 재작성은 새 행이 아니라 되살리기다.
@Entity
@Table(
    name = "member_profile",
    uniqueConstraints = [UniqueConstraint(name = "uk_member_profile_member", columnNames = ["member_id"])],
)
class MemberProfileEntity(
    id: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    bio: String = "",
) : UuidBaseEntity(id) {
    var bio: String = bio
        protected set

    fun updateProfile(bio: String) {
        this.bio = bio
    }
}
