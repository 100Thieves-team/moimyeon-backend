package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

// 관심에서 뺀 것은 소프트 삭제로 남긴다. 유니크에 _active_check 가 붙어 있어
// 뺐다가 다시 담으면 새 행으로 들어간다 — 언제부터 관심이었는지가 created_at 에 남는다.
@Entity
@Table(
    name = "member_profile_interest_company",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_member_profile_interest_company_active",
            columnNames = ["profile_id", "company_id", "_active_check"],
        ),
    ],
)
class MemberProfileInterestCompanyEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val profileId: UUID,
    val companyId: Long,
) : BaseEntity()
