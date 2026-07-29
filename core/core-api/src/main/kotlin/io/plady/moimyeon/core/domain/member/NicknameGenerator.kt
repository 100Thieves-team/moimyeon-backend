package io.plady.moimyeon.core.domain.member

import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.random.Random

@Component
class NicknameGenerator(
    private val memberFinder: MemberFinder,
) {
    fun generate(): Nickname {
        return Nickname("${ADJECTIVES.random()} ${ANIMALS.random()} ${"%02d".format(Random.nextInt(1, 100))}")
    }

    // ATTEMPTS 전부 충돌하면 UUID 기반 fallback. fallback 도 점유 확인을 거치고, 그래도 전부
    // 충돌하면 마지막 후보를 그대로 반환한다(최종 방어선은 DB 유니크 제약 uk_member_nickname).
    fun generateUnique(): Nickname {
        repeat(MAX_ATTEMPTS) {
            val candidate = generate()
            if (memberFinder.isNicknameAvailable(candidate)) {
                return candidate
            }
        }
        repeat(MAX_ATTEMPTS) {
            val fallback = fallbackCandidate()
            if (memberFinder.isNicknameAvailable(fallback)) {
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
