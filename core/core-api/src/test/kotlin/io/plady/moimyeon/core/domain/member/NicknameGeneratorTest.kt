package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class NicknameGeneratorTest {
    private val memberRepository = mockk<MemberRepository>()
    private val generator = NicknameGenerator(memberRepository)

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
    fun `모든 후보가 사용 중이면 사용 가능한 UUID 기반 fallback 닉네임을 반환한다`() {
        // 후보 20회는 충돌, 첫 fallback 은 사용 가능
        every { memberRepository.existsByNickname(any()) } returnsMany List(20) { true } + false

        val nickname = generator.generateUnique()

        assertThat(nickname.value).matches("^면접자 [0-9a-f]{8}$")
    }

    @Test
    fun `fallback 닉네임도 점유 확인을 거치며 충돌하면 새 fallback 으로 재시도한다`() {
        // 후보 20회 + 첫 fallback 은 충돌, 두 번째 fallback 이 사용 가능
        every { memberRepository.existsByNickname(any()) } returnsMany List(21) { true } + false

        val nickname = generator.generateUnique()

        assertThat(nickname.value).matches("^면접자 [0-9a-f]{8}$")
        verify(exactly = 22) { memberRepository.existsByNickname(any()) }
    }

    @Test
    fun `fallback 까지 전부 충돌해도 마지막 후보를 반환해 종료를 보장한다(최종 방어선은 DB 유니크 제약)`() {
        every { memberRepository.existsByNickname(any()) } returns true

        val nickname = generator.generateUnique()

        assertThat(nickname.value).startsWith("면접자 ")
    }
}
