package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.company.Company

data class CompanySearchResponse(
    val companies: List<CompanyResponse>,
) {
    companion object {
        fun from(companies: List<Company>): CompanySearchResponse {
            return CompanySearchResponse(companies.map { CompanyResponse(it.id, it.name) })
        }
    }
}

data class CompanyResponse(
    val companyId: Long,
    val name: String,
)
