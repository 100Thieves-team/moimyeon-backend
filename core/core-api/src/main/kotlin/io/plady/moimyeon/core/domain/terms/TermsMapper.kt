package io.plady.moimyeon.core.domain.terms

import io.plady.moimyeon.storage.db.core.TermsEntity

object TermsMapper {
    fun toDomain(entity: TermsEntity): Terms = Terms(
        id = entity.id,
        type = entity.type,
        version = entity.version,
        title = entity.title,
        content = entity.content,
        required = entity.required,
        effectiveFrom = entity.effectiveFrom,
        status = entity.status,
    )
}
