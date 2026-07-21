package io.plady.moimyeon.admin.support.error

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdminErrorTypeTest {
    @Test
    fun `AdminErrorCode_중복_사용_확인`() {
        val codes = AdminErrorType.entries.map { it.code }
        val duplicates = codes.groupingBy { it }.eachCount().filter { it.value > 1 }.keys

        assertTrue(duplicates.isEmpty(), "중복된 AdminErrorCode가 있습니다: $duplicates")
    }

    @Test
    fun `AdminErrorCode가_AdminErrorType에서_모두_사용되는지_확인`() {
        val declaredCodes = AdminErrorCode.values().toSet()
        val usedCodes = AdminErrorType.values().map { it.code }.toSet()

        val unused = declaredCodes - usedCodes

        assertTrue(unused.isEmpty(), "사용되지 않은 AdminErrorCode가 있습니다: $unused")
    }
}
