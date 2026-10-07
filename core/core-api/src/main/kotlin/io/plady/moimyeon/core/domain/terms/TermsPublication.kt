package io.plady.moimyeon.core.domain.terms

import io.plady.moimyeon.core.enums.TermsType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

object TermsPublication {
    private val publicationZone = ZoneId.of("Asia/Seoul")

    // effective_from은 한국 시행시각이다. 서버 기본 시각대(운영: UTC)에 의존하지 않는다.
    fun now(clock: Clock): LocalDateTime = LocalDateTime.now(clock.withZone(publicationZone))

    fun selectCurrent(effectiveTerms: List<Terms>): List<Terms> {
        val byType = effectiveTerms.groupBy { it.type }
        return TermsType.entries.map { type ->
            val candidates = byType[type].orEmpty()
            requireBusiness(candidates.isNotEmpty(), CoreErrorType.TERMS_UNAVAILABLE)
            val latestAt = candidates.maxOf { it.effectiveFrom }
            val latest = candidates.filter { it.effectiveFrom == latestAt }
            requireBusiness(latest.size == 1, CoreErrorType.TERMS_UNAVAILABLE)
            latest.single()
        }
    }
}
