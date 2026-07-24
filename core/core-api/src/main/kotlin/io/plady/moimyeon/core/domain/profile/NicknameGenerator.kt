package io.plady.moimyeon.core.domain.profile

import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class NicknameGenerator {
    fun generate(): Nickname {
        return Nickname("${ADJECTIVES.random()} ${ANIMALS.random()} ${"%02d".format(Random.nextInt(1, 100))}")
    }

    companion object {
        private val ADJECTIVES = listOf("집요한", "꼼꼼한", "차분한", "명랑한", "성실한", "영리한", "다정한", "용감한")
        private val ANIMALS = listOf("수달", "고슴도치", "펭귄", "라쿤", "알파카", "해달", "부엉이", "치타")
    }
}
