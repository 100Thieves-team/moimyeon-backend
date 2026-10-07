package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.DoExampleRequest
import io.plady.moimyeon.core.api.controller.v1.response.ExampleItemResponseDto
import io.plady.moimyeon.core.api.controller.v1.response.ExampleResponseDto
import io.plady.moimyeon.core.domain.example.ExampleData
import io.plady.moimyeon.core.domain.example.ExampleService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
class ExampleController(
    val exampleExampleService: ExampleService,
) {
    @GetMapping("/get/{exampleValue}")
    fun exampleGet(
        @PathVariable exampleValue: String,
        @RequestParam exampleParam: String,
    ): ApiResponse<ExampleResponseDto> {
        val result = exampleExampleService.processExample(ExampleData(exampleValue, exampleParam))
        return ApiResponse.success(ExampleResponseDto(result.data, LocalDate.now(), LocalDateTime.now(), ExampleItemResponseDto.build()))
    }

    @PostMapping("/post")
    fun examplePost(
        @RequestBody request: DoExampleRequest,
    ): ApiResponse<ExampleResponseDto> {
        val result = exampleExampleService.processExample(request.toExampleData())
        return ApiResponse.success(ExampleResponseDto(result.data, LocalDate.now(), LocalDateTime.now(), ExampleItemResponseDto.build()))
    }
}
