package io.plady.moimyeon.core.domain.member

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.random.Random

private val log = KotlinLogging.logger {}

@Component
class NicknameGenerator(
    private val memberRepository: MemberRepository,
) {
    fun generate(): Nickname {
        log.debug { "nickname.generator.generate" }
        return Nickname("${ADJECTIVES.random()} ${ANIMALS.random()} ${"%02d".format(Random.nextInt(1, 100))}")
    }

    // TODO: 실 배포 전에 닉네임 생성 설계 다시하기
    fun generateUnique(): Nickname {
        log.debug { "nickname.generator.generateUnique" }
        repeat(MAX_ATTEMPTS) {
            val candidate = generate()
            if (!memberRepository.existsByNickname(candidate.value)) {
                return candidate
            }
        }
        repeat(MAX_ATTEMPTS) {
            val fallback = fallbackCandidate()
            if (!memberRepository.existsByNickname(fallback.value)) {
                return fallback
            }
        }
        return fallbackCandidate()
    }

    private fun fallbackCandidate(): Nickname {
        return Nickname("면접자 ${UUID.randomUUID().toString().take(8)}")
    }

    companion object {
        private const val MAX_ATTEMPTS = 20
        private val ADJECTIVES = listOf("집요한", "꼼꼼한", "차분한", "명랑한", "성실한", "영리한", "다정한", "용감한")
        private val ANIMALS = listOf("수달", "고슴도치", "펭귄", "라쿤", "알파카", "해달", "부엉이", "치타")
    }
}
