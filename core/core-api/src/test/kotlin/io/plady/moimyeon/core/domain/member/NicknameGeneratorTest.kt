package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class NicknameGeneratorTest {
    // Nickname 은 value class 라 mockk 매처(any)가 깨진다. String 을 받는 저장소 레벨로 스텁한다.
    private val memberRepository = mockk<MemberRepository>()
    private val generator = NicknameGenerator(MemberFinder(memberRepository))

    @RepeatedTest(20)
    fun `생성된 닉네임은 형식 규칙을 통과하는 '형용사 동물 NN' 형태다`() {
        val nickname = generator.generate() // Nickname 생성 자체가 형식 검증

        assertThat(nickname.value).matches("^[가-힣]+ [가-힣]+ \\d{2}$")
    }

    @Test
    fun `사용 가능한 후보가 나오면 그 닉네임을 반환한다`() {
        every { memberRepository.existsByNickname(any()) } returns false

        val nickname = generator.generateUnique()

        assertThat(nickname.value).matches("^[가-힣]+ [가-힣]+ \\d{2}$")
    }

    @Test
    fun `모든 후보가 사용 중이면 UUID 기반 fallback 닉네임으로 유일성을 보장한다`() {
        every { memberRepository.existsByNickname(any()) } returns true

        val nickname = generator.generateUnique()

        assertThat(nickname.value).startsWith("면접자 ")
    }
}
