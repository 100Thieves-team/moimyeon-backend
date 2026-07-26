package io.plady.moimyeon.core.api.controller.v1.response

// 이력서 업로드/조회(B·08) 응답. AI 요약은 업로드 직후 PROCESSING → 조회 시 DONE 으로 채워진다(§4.5).
data class ResumeResponse(
    val resumeId: Long,
    val fileName: String,
    val fileSize: Long,
    val fileSizeLabel: String, // 240KB
    val status: String, // UPLOADED
    val aiSummary: AiSummaryResponse,
)

data class AiSummaryResponse(
    val status: String, // PROCESSING | DONE | FAILED
    val text: String?,
)
