package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.SigunguLevel
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "sigungu",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sigungu_sigungu_code", columnNames = ["sigungu_code"]),
        UniqueConstraint(name = "uk_sigungu_sido_name", columnNames = ["sido_id", "name"]),
    ],
)
class SigunguEntity(
    val sidoId: Long,
    val sigunguCode: String?,
    val name: String,
    @Enumerated(EnumType.STRING)
    val level: SigunguLevel,
    val sortOrder: Short?,
) : BaseEntity()
