package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

// append-only 이력. 쌓인 사실은 고치지 않고 deleted_at 으로 가리는 것만 허용한다.
// 재동의는 되살리기가 아니라 새 행 append 다(schema.sql 의 _active_check).
@Entity
@Table(
    name = "terms_agreement",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_terms_agreement_member_terms_active",
            columnNames = ["member_id", "terms_id", "_active_check"],
        ),
    ],
)
class TermsAgreementEntity(
    id: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val termsId: UUID,
    val agreedAt: LocalDateTime,
) : UuidBaseEntity(id)
