package io.plady.moimyeon.core.domain.terms

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TermsService(
    private val termsFinder: TermsFinder,
) {
    fun getActiveTerms(): List<Terms> = termsFinder.getActive()

    fun getTerms(termsId: UUID): Terms = termsFinder.getById(termsId)
}
