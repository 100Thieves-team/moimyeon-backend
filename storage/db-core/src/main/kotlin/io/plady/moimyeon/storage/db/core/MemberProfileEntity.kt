package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MeetingPreference
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

// 관심 회사·관심 직무는 M:N 조인 엔티티로 분리되어 있다
// (MemberProfileInterestCompanyEntity / MemberProfileInterestJobRoleEntity).
// 연관관계를 걸지 않는 컨벤션에 따라 여기서 컬렉션을 들지 않고, ProfileManager 가 함께 다룬다.
@Entity
@Table(name = "member_profile")
class MemberProfileEntity(
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    bio: String? = null,
    meetingPreference: MeetingPreference? = null,
    sigunguId: Long? = null,
) : AbstractEntity() {
    var bio: String? = bio
        protected set

    @Enumerated(EnumType.STRING)
    var meetingPreference: MeetingPreference? = meetingPreference
        protected set

    var sigunguId: Long? = sigunguId
        protected set

    // 전체 교체 수정. 저장은 변경 감지에 맡긴다(save 호출 없음).
    fun updateProfile(
        bio: String?,
        meetingPreference: MeetingPreference?,
        sigunguId: Long?,
    ) {
        this.bio = bio
        this.meetingPreference = meetingPreference
        this.sigunguId = sigunguId
    }
}
