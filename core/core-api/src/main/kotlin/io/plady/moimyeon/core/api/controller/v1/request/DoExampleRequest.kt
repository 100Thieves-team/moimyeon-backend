package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.ExampleData

data class DoExampleRequest(
    val data: String,
) {
    fun toExampleData(): ExampleData {
        return ExampleData(data, data)
    }
}
