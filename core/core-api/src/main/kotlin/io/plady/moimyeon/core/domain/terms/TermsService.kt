package io.plady.moimyeon.core.domain.terms

import org.springframework.stereotype.Service

@Service
class TermsService(
    private val termsFinder: TermsFinder,
) {
    fun getActiveTerms(): List<Terms> = termsFinder.findActive()
}
