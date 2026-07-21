package io.plady.moimyeon.core.api.controller.v1.response

import java.time.LocalDate
import java.time.LocalDateTime

data class ExampleResponseDto(
    val result: String,
    val date: LocalDate,
    val datetime: LocalDateTime,
    val items: List<ExampleItemResponseDto>,
)

data class ExampleItemResponseDto(
    val key: String,
) {
    companion object {
        fun build(): List<ExampleItemResponseDto> {
            return listOf(ExampleItemResponseDto("1"), ExampleItemResponseDto("2"))
        }
    }
}
