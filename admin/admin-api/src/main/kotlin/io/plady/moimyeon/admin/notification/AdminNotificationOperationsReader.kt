package io.plady.moimyeon.admin.notification

fun interface AdminNotificationOperationsReader {
    fun loadDashboard(recentDeadLetterLimit: Int): AdminNotificationDashboard
}

data class AdminNotificationDashboard(
    val pendingCount: Long,
    val deadLetterCount: Long,
    val recentDeadLetters: List<AdminDeadLetterMessage>,
)

data class AdminDeadLetterMessage(
    val recordId: String,
    val sourceRecordId: String,
    val eventId: String,
    val eventType: String,
    val channel: String,
    val failureType: String,
    val originalFailureType: String,
    val failureMessage: String,
    val attemptCount: String,
    val failedAt: String,
    val payload: String,
)
