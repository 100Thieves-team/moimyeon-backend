package io.plady.moimyeon.core.domain.resume

class ResumeVault(
    val resumes: List<Resume>,
) {
    val maxCount: Int = RESUME_VAULT_MAX_COUNT
    val defaultResume: Resume? = resumes.singleOrNull { it.isDefault }

    init {
        require(resumes.size <= maxCount) { "이력서 보관함은 최대 $maxCount 개까지 보관할 수 있습니다." }
        require(resumes.isEmpty() || defaultResume != null) { "비어 있지 않은 이력서 보관함에는 기본 이력서가 하나 있어야 합니다." }
    }
}

internal const val RESUME_VAULT_MAX_COUNT = 10
