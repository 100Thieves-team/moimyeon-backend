package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "sido")
class SidoEntity(
    val sidoCode: String,
    val name: String,
    val shortName: String,
    val isMetro: Boolean,
    val sortOrder: Short?,
    val retiredAt: LocalDateTime? = null,
) : BaseEntity()
