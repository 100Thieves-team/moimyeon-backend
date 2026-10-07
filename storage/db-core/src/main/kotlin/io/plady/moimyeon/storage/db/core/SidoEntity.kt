package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "sido",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sido_sido_code", columnNames = ["sido_code"]),
        UniqueConstraint(name = "uk_sido_name", columnNames = ["name"]),
        UniqueConstraint(name = "uk_sido_short_name", columnNames = ["short_name"]),
    ],
)
class SidoEntity(
    val sidoCode: String,
    val name: String,
    val shortName: String,
    val isMetro: Boolean,
    val sortOrder: Short?,
) : BaseEntity()
