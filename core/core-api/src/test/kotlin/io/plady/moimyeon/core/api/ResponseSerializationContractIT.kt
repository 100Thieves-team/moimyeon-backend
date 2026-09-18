package io.plady.moimyeon.core.api

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.api.controller.v1.response.RoomViewerResponse
import io.plady.moimyeon.core.api.controller.v1.response.ViewerMemberResponse
import io.plady.moimyeon.core.api.controller.v1.response.ViewerQuotaResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

// 웹 응답 직렬화 계약 — 앱 컨텍스트의 매퍼(Jackson 3, tools.jackson)로 프로퍼티 이름을 고정한다.
//
// 배경: kotlin 모듈(tools.jackson.module:jackson-module-kotlin)이 prod runtimeClasspath 에 없으면
// Kotlin `is` 접두 프로퍼티가 bean 규칙으로 접두가 잘려(`isPassed`→`passed`) REST Docs 계약과 실응답이
// 갈린다 — MOI-500 배포 직후 실제로 있었던 회귀다.
//
// ⚠️ 이 테스트는 부분 가드다. testRuntimeClasspath 에는 kotlin 모듈이 전이 의존성으로 항상 실려
// 의존성 선언이 빠지는 회귀는 여기서 안 잡힌다(테스트≠프로덕션 클래스패스) — 그 가드는
// core-api build.gradle.kts 의 명시 선언과 주석이 맡는다. 여기는 매퍼 설정 변경으로
// 이름 규칙이 바뀌는 회귀만 잡는다.
class ResponseSerializationContractIT(
    private val objectMapper: ObjectMapper,
) : ContextTest() {
    @Test
    fun `is·has 접두 프로퍼티는 접두를 유지한 이름으로 직렬화된다`() {
        val viewerJson = objectMapper.writeValueAsString(
            RoomViewerResponse(
                isHost = false,
                isParticipating = false,
                hasRemovalHistory = false,
                latestApplicationStatus = null,
                member = ViewerMemberResponse(
                    isActive = true,
                    participationSlots = ViewerQuotaResponse(occupied = 1, limit = 3),
                    pendingApplicationQuota = ViewerQuotaResponse(occupied = 0, limit = 3),
                ),
            ),
        )
        assertThat(viewerJson)
            .contains("\"isHost\"", "\"isParticipating\"", "\"hasRemovalHistory\"", "\"isActive\"")
            .doesNotContain("\"host\"", "\"participating\"", "\"removalHistory\"", "\"active\"")
    }
}
