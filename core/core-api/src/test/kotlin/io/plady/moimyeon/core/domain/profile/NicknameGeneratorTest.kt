package io.plady.moimyeon.core.domain.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest

class NicknameGeneratorTest {
    private val generator = NicknameGenerator()

    @RepeatedTest(20)
    fun `생성된 닉네임은 형식 규칙을 통과하는 '형용사 동물 NN' 형태다`() {
        val nickname = generator.generate() // Nickname 생성 자체가 형식 검증

        assertThat(nickname.value).matches("^[가-힣]+ [가-힣]+ \\d{2}$")
    }
}
