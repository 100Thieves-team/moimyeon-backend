package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.room.RoomCursor
import io.plady.moimyeon.core.domain.room.RoomSortOrder
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

class RoomSearchCursorTokenTest {
    private val cursor = RoomCursor(
        sortValue = LocalDateTime.of(2026, 8, 12, 19, 0),
        id = UUID.fromString("018f2c00-0000-7000-8000-000000000001"),
    )

    @Test
    fun `커서를 인코딩한 뒤 디코딩하면 같은 값이 나온다`() {
        val token = RoomSearchCursorToken.encode(cursor, RoomSortOrder.SCHEDULE)

        assertThat(RoomSearchCursorToken.decode(token, RoomSortOrder.SCHEDULE)).isEqualTo(cursor)
    }

    // 일반 Base64 를 쓰면 '+' 가 쿼리스트링에서 공백으로 해석돼 커서가 조용히 깨진다.
    // 한 건만 보면 우연히 통과할 수 있어 여러 id 로 확인한다.
    @Test
    fun `인코딩한 커서는 URL 에 그대로 실을 수 있는 문자만 담는다`() {
        val tokens = URL_UNSAFE_PRONE_IDS.map {
            RoomSearchCursorToken.encode(cursor.copy(id = UUID.fromString(it)), RoomSortOrder.SCHEDULE)
        }

        assertThat(tokens).allMatch { it.matches(URL_SAFE_ALPHABET) }
    }

    // 정렬이 바뀌면 sortValue 의 의미가 start_at ↔ created_at 으로 달라진다.
    // 막지 않으면 예외 없이 엉뚱한 컬럼과 비교되므로, 조용히 이어붙지 않고 여기서 끊는다.
    @Test
    fun `정렬이 다른 커서는 디코딩되지 않는다`() {
        val token = RoomSearchCursorToken.encode(cursor, RoomSortOrder.SCHEDULE)

        assertDecodeFails(token)
    }

    @Test
    fun `형식이 맞지 않는 커서 토큰은 디코딩되지 않는다`() {
        listOf(
            "not-a-token", // Base64 로는 풀리지만 구분자가 없다
            "!!!", // Base64 알파벳이 아니다
            encodePayload("v2|SCHEDULE|2026-08-12T19:00|$SAMPLE_ID"), // 포맷 버전이 다르다
            encodePayload("v1|SCHEDULE|어제|$SAMPLE_ID"), // 시각이 파싱되지 않는다
            encodePayload("v1|SCHEDULE|2026-08-12T19:00|nope"), // id 가 UUID 가 아니다
        ).forEach { assertDecodeFails(it, RoomSortOrder.SCHEDULE) }
    }

    private fun assertDecodeFails(token: String, sort: RoomSortOrder = RoomSortOrder.RECENT) {
        assertThatThrownBy { RoomSearchCursorToken.decode(token, sort) }
            .isInstanceOfSatisfying(CoreApiException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreApiErrorType.INVALID_REQUEST)
            }
    }

    private fun encodePayload(payload: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))

    companion object {
        private const val SAMPLE_ID = "018f2c00-0000-7000-8000-000000000001"
        private val URL_SAFE_ALPHABET = Regex("[A-Za-z0-9_-]+")
        private val URL_UNSAFE_PRONE_IDS = listOf(
            "018f2c00-0000-7000-8000-000000000001",
            "ffffffff-ffff-4fff-bfff-ffffffffffff",
            "3f3f3f3f-3f3f-4f3f-bf3f-3f3f3f3f3f3f",
            "7e7e7e7e-7e7e-4e7e-be7e-7e7e7e7e7e7e",
            "fbfbfbfb-fbfb-4bfb-bbfb-fbfbfbfbfbfb",
        )
    }
}
