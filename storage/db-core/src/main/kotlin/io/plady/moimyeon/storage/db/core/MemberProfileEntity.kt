package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MeetingPreference
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
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
    var jobRoleId: Long? = null,
    var bio: String? = null,
    @Enumerated(EnumType.STRING)
    var meetingPreference: MeetingPreference? = null,
    var sigunguId: Long? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "member_profile_interest",
        joinColumns = [JoinColumn(name = "member_id")],
        uniqueConstraints = [UniqueConstraint(name = "uk_member_profile_interest", columnNames = ["member_id", "company_id"])],
    )
    @Column(name = "company_id")
    var interestCompanyIds: MutableList<Long> = mutableListOf(),
) {
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN

    // 전체 교체 수정. 저장은 변경 감지에 맡긴다(save 호출 없음).
    fun updateProfile(
        jobRoleId: Long?,
        bio: String?,
        meetingPreference: MeetingPreference?,
        sigunguId: Long?,
        interestCompanyIds: List<Long>,
    ) {
        this.jobRoleId = jobRoleId
        this.bio = bio
        this.meetingPreference = meetingPreference
        this.sigunguId = sigunguId
        this.interestCompanyIds.clear()
        this.interestCompanyIds.addAll(interestCompanyIds)
    }
}
