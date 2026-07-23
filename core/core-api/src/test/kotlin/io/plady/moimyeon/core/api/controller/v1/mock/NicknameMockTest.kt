package io.plady.moimyeon.core.api.controller.v1.mock

import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class NicknameMockTest {
    @Test
    fun `추천 닉네임은 형식에 맞고 사용 가능한 값이다`() {
        NicknameMock.validateFormat(NicknameMock.SUGGESTED)
        assertThat(NicknameMock.isAvailable(NicknameMock.SUGGESTED)).isTrue()
    }

    @Test
    fun `형식 위반 닉네임은 E1005 를 던진다`() {
        assertThatThrownBy { NicknameMock.validateFormat("한") }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType")
            .isEqualTo(ErrorType.INVALID_NICKNAME)

        assertThatThrownBy { NicknameMock.validateFormat("금지!문자@") }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType")
            .isEqualTo(ErrorType.INVALID_NICKNAME)
    }

    @Test
    fun `예약 닉네임만 사용 불가다`() {
        assertThat(NicknameMock.isAvailable("집요한 수달 07")).isFalse()
        assertThat(NicknameMock.isAvailable("차분한 펭귄 12")).isTrue()
    }
}
