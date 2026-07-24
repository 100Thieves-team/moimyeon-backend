package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.SigunguLevel
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "sigungu")
class SigunguEntity(
    val sidoId: Long,
    val sigunguCode: String?,
    val name: String,
    @Enumerated(EnumType.STRING)
    val level: SigunguLevel,
    val sortOrder: Short?,
    val retiredAt: LocalDateTime? = null,
) : BaseEntity()
