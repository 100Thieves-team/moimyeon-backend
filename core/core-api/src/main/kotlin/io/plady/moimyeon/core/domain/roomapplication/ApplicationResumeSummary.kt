package io.plady.moimyeon.core.domain.roomapplication

sealed interface ApplicationResumeSummary {
    data class Ready(
        val content: String,
    ) : ApplicationResumeSummary

    data object Preparing : ApplicationResumeSummary
}
