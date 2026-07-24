package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.enums.TermsType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "terms")
class TermsEntity(
    id: UUID,
    @Enumerated(EnumType.STRING)
    val type: TermsType,
    val version: String,
    val title: String,
    val content: String,
    val required: Boolean,
    val effectiveFrom: LocalDateTime,
    @Enumerated(EnumType.STRING)
    var status: TermsStatus,
) : UuidBaseEntity(id)
