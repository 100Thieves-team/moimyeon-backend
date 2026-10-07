package io.plady.moimyeon.core.domain.catalog

data class Sido(
    val id: Long,
    val name: String,
    val shortName: String,
    val sigungus: List<Sigungu>,
)
