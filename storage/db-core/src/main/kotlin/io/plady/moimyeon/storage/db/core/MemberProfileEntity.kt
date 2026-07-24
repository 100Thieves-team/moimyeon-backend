package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MeetingPreference
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "member_profile")
class MemberProfileEntity(
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    var nickname: String,
    var jobTitle: String? = null,
    var bio: String? = null,
    @Enumerated(EnumType.STRING)
    var meetingPreference: MeetingPreference? = null,
    var region: String? = null,
) {
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN
}
