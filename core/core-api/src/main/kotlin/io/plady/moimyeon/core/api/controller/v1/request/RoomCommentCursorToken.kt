package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.roomcomment.RoomCommentCursor
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

// 방명록 목록 커서의 와이어 포맷. FE 는 내용을 해석하지 않고 받은 문자열을 그대로 되돌려준다
// (룸 탐색 RoomSearchCursorToken 과 같은 태도). 정렬이 최신순 하나뿐이라 정렬 종류는 싣지 않는다.
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

    // ISO-8601 시각에 ':' 가 들어 있어 ':' 는 구분자로 쓸 수 없다.
    private const val DELIMITER = "|"

    private const val PART_COUNT = 3
    private const val VERSION_INDEX = 0
    private const val CREATED_AT_INDEX = 1
    private const val ID_INDEX = 2

    // 쿼리스트링에 그대로 실리므로 URL 안전 알파벳을 쓰고 패딩('=')도 남기지 않는다.
    private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val DECODER: Base64.Decoder = Base64.getUrlDecoder()
}
