package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

// 프로필↔직무의 M:N 조인. 한 사람이 백엔드와 데이터 엔지니어를 함께 준비하는 일이 흔하다.
// 양쪽이 다 엔티티라 값 컬렉션이 아니라 엔티티로 둔다(설명은 MemberProfileInterestCompanyEntity 참고).
@Entity
@Table(
    name = "member_profile_interest_job_role",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_member_profile_interest_job_role_active",
            columnNames = ["member_id", "job_role_id", "_active_check"],
        ),
    ],
)
class MemberProfileInterestJobRoleEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    val jobRoleId: Long,
) : BaseEntity()
