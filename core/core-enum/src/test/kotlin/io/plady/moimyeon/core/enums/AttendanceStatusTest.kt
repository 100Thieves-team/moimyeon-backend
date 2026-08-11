package io.plady.moimyeon.core.enums

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AttendanceStatusTest {
    @Test
    fun `출석 상태는 출석과 불참 두 종류뿐이다`() {
        assertThat(AttendanceStatus.entries)
            .containsExactlyInAnyOrder(AttendanceStatus.ATTENDED, AttendanceStatus.ABSENT)
    }
}
