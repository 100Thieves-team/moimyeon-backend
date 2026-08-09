package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.room.RoomCursor
import io.plady.moimyeon.core.domain.room.RoomSortOrder
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.UUID

// 커서의 와이어 포맷(「룸 탐색」 §4.1 "불투명 토큰"). FE 는 내용을 해석하지 않고 받은 문자열을 그대로 되돌려준다.
// 도메인이 아니라 여기 있는 이유: 인코딩 방식은 HTTP 로 값을 실어 나르는 방법일 뿐 룸의 규칙이 아니다.
//
// 정렬 종류를 함께 싣는 이유가 핵심이다. 정렬이 바뀌면 sortValue 의 의미가 start_at ↔ created_at 으로
// 달라지는데 둘 다 LocalDateTime 이라, 검사하지 않으면 엉뚱한 컬럼과 비교되어 예외 없이 잘못된 목록이 나간다.
//
// 반대로 필터 조건은 싣지 않는다. 필터가 바뀌면 FE 가 목록을 처음부터 다시 그리므로 서버가 막을 이유가 없고,
// 조건 정규화(미지정 vs false, 파라미터 순서)가 한 군데만 어긋나도 멀쩡한 커서가 400 이 된다.
object RoomSearchCursorToken {
    fun encode(cursor: RoomCursor, sort: RoomSortOrder): String {
        val payload = listOf(VERSION, sort.name, cursor.sortValue.toString(), cursor.id.toString())
            .joinToString(DELIMITER)
        return ENCODER.encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(token: String, sort: RoomSortOrder): RoomCursor {
        val parts = decodePayload(token).split(DELIMITER)
        if (parts.size != PART_COUNT || parts[VERSION_INDEX] != VERSION || parts[SORT_INDEX] != sort.name) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return try {
            RoomCursor(
                sortValue = LocalDateTime.parse(parts[SORT_VALUE_INDEX]),
                id = UUID.fromString(parts[ID_INDEX]),
            )
        } catch (e: DateTimeParseException) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        } catch (e: IllegalArgumentException) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
    }

    private fun decodePayload(token: String): String = try {
        String(DECODER.decode(token), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
    }

    // 커서 포맷을 바꿀 때 이 값을 올린다. 순회 중이던 옛 토큰이 조용히 오해석되지 않고 400 으로 끊긴다.
    private const val VERSION = "v1"

    // ISO-8601 시각에 ':' 가 들어 있어 ':' 는 구분자로 쓸 수 없다.
    private const val DELIMITER = "|"

    private const val PART_COUNT = 4
    private const val VERSION_INDEX = 0
    private const val SORT_INDEX = 1
    private const val SORT_VALUE_INDEX = 2
    private const val ID_INDEX = 3

    // 쿼리스트링에 그대로 실리므로 URL 안전 알파벳을 쓰고 패딩('=')도 남기지 않는다.
    // 일반 Base64 를 쓰면 '+' 가 공백으로 해석돼 커서가 조용히 깨진다.
    private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val DECODER: Base64.Decoder = Base64.getUrlDecoder()
}
