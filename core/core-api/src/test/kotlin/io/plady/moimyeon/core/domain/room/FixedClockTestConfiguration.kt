package io.plady.moimyeon.core.domain.room

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

// 룸 생성 트랜잭션의 시각이 하나인지를 단언하려면 그 시각을 테스트가 알아야 한다.
// 룸 생성 경로와 신청 경로가 같은 Clock 을 보므로(RoomValidator 의 일정 게이트 포함)
// 두 IT 가 이 설정 하나를 공유해 컨텍스트도 하나만 뜬다.
val FIXED_NOW: LocalDateTime = LocalDateTime.of(2026, 8, 11, 12, 0)

@TestConfiguration(proxyBeanMethods = false)
class FixedClockTestConfiguration {
    @Bean
    @Primary
    fun fixedRoomClock(): Clock = Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
}
