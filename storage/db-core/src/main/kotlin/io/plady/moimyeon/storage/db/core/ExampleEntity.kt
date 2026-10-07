package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity

@Entity
class ExampleEntity(
    val exampleColumn: String,
) : BaseEntity()
