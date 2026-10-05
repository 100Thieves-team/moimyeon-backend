package io.plady.moimyeon.storage.db.core.qa

/** QA 회원 이메일 형식: prefix 로 시작하고 suffix 로 끝난다. 가운데는 회원마다 다른 키다. */
data class QaEmailPattern(
    val prefix: String,
    val suffix: String,
)
