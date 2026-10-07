package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.Sido

data class RegionsResponse(
    val sidos: List<SidoResponse>,
) {
    companion object {
        fun from(sidos: List<Sido>): RegionsResponse {
            return RegionsResponse(
                sidos.map { sido ->
                    SidoResponse(
                        name = sido.name,
                        shortName = sido.shortName,
                        sigungus = sido.sigungus.map { SigunguResponse(it.id, it.name) },
                    )
                },
            )
        }
    }
}

data class SidoResponse(
    val name: String,
    val shortName: String,
    val sigungus: List<SigunguResponse>,
)

data class SigunguResponse(
    val sigunguId: Long,
    val name: String,
)
