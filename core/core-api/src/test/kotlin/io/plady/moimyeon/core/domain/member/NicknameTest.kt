package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class NicknameTest {
    private fun assertInvalid(value: String) {
        assertThatThrownBy { Nickname(value) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_NICKNAME)
            }
    }

    @Test
    fun `한글·영문·숫자·공백 조합의 2~20자 닉네임을 생성할 수 있다`() {
        assertThat(Nickname("집요한 수달 07").value).isEqualTo("집요한 수달 07")
        assertThat(Nickname("dev otter 7").value).isEqualTo("dev otter 7")
    }

    @Test
    fun `공백만으로는 만들 수 없다`() {
        assertInvalid("   ")
    }

    @Test
    fun `길이 범위를 벗어나면 E1005 를 던진다`() {
        assertInvalid("한")
        assertInvalid("가".repeat(21))
    }

    @Test
    fun `허용되지 않는 문자가 있으면 E1005 를 던진다`() {
        assertInvalid("금지!문자@")
        assertInvalid("hyphen-name")
    }

    @Test
    fun `금칙어가 포함되면 E1005 를 던진다`() {
        assertInvalid("모이면 운영자")
        assertInvalid("공식 관리자 01")
    }
}
