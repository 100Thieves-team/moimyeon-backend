package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@MappedSuperclass
abstract class UuidBaseEntity(
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    val id: UUID,
) : AbstractEntity()
