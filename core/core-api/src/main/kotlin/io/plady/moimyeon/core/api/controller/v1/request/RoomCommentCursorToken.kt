package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.roomcomment.RoomCommentCursor
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

// 포맷 규칙(VERSION 올리기, 구분자, URL 안전 Base64)은 RoomSearchCursorToken 과 같다.
// 정렬이 최신순 하나뿐이라 정렬 종류는 싣지 않는다.
object RoomCommentCursorToken {
    fun encode(cursor: RoomCommentCursor): String {
        val payload = listOf(VERSION, cursor.createdAt.toString(), cursor.id.toString()).joinToString(DELIMITER)
        return ENCODER.encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(token: String): RoomCommentCursor {
        val parts = decodePayload(token).split(DELIMITER)
        if (parts.size != PART_COUNT || parts[VERSION_INDEX] != VERSION) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return try {
            RoomCommentCursor(
                createdAt = LocalDateTime.parse(parts[CREATED_AT_INDEX]),
                id = parts[ID_INDEX].toLong(),
            )
        } catch (e: DateTimeParseException) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        } catch (e: NumberFormatException) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
    }

    private fun decodePayload(token: String): String = try {
        String(DECODER.decode(token), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
    }

    // 커서 포맷을 바꿀 때 이 값을 올린다. 순회 중이던 옛 토큰이 오해석되지 않고 400 으로 끊긴다.
    private const val VERSION = "v1"

    private const val DELIMITER = "|"

    private const val PART_COUNT = 3
    private const val VERSION_INDEX = 0
    private const val CREATED_AT_INDEX = 1
    private const val ID_INDEX = 2

    private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val DECODER: Base64.Decoder = Base64.getUrlDecoder()
}
