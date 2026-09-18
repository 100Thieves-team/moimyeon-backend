package io.plady.moimyeon.core.domain.roomcomment

data class RoomCommentPage(
    val comments: List<RoomComment>,
    val nextCursor: RoomCommentCursor?,
)

data class RoomCommentListing(
    val window: RoomCommentWindow,
    val page: RoomCommentPage,
)
