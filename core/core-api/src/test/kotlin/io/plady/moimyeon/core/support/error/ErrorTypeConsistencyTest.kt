package io.plady.moimyeon.core.support.error

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// ErrorCode 네임스페이스는 CoreErrorType(도메인)과 CoreApiErrorType(api)이 공유하므로 합집합으로 검증한다.
class ErrorTypeConsistencyTest {
    private val usedCodes = CoreErrorType.entries.map { it.code } + CoreApiErrorType.entries.map { it.code }

    @Test
    fun `ErrorCode_중복_사용_확인`() {
        val duplicates = usedCodes.groupingBy { it }.eachCount().filter { it.value > 1 }.keys

        assertTrue(duplicates.isEmpty(), "중복된 ErrorCode가 있습니다: $duplicates")
    }

    @Test
    fun `ErrorCode가_모두_사용되는지_확인`() {
        val unused = ErrorCode.entries.toSet() - usedCodes.toSet()

        assertTrue(unused.isEmpty(), "사용되지 않은 ErrorCode가 있습니다: $unused")
    }
}
