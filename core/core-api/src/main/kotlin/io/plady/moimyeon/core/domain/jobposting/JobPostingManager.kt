package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.storage.db.core.JobPostingEntity
import io.plady.moimyeon.storage.db.core.JobPostingRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

@Component
class JobPostingManager(
    private val jobPostingRepository: JobPostingRepository,
) {
    // 링크로 공고를 즉시 생성하고 그 id 를 반환한다(「룸 생성」 §4.1). 같은 URL 재요청은 새로 만들지 않고
    // 기존 공고를 돌려준다 — source_uid 를 URL 에서 결정론적으로 발급(앱 전용 접두 lnk:)하고,
    // uk_job_posting_source_uid 유니크가 멱등성의 최종 보장이다(schema.sql 의 링크 생성 규칙).
    // verified=false·is_open=true 로 저장해 탐색 필터에선 숨되 그 공고로 룸 생성은 바로 가능하다.
    @Transactional
    fun create(command: JobPostingCreationCommand, createdByMemberId: UUID): Long {
        val sourceUid = sourceUidOf(command.url)
        jobPostingRepository.findBySourceUidAndDeletedAtIsNull(sourceUid)?.let { return it.id }
        return try {
            jobPostingRepository.saveAndFlush(
                JobPostingEntity(
                    sourceUid = sourceUid,
                    companyId = command.companyId,
                    title = command.postingName,
                    isOpen = true,
                    sourceUrl = command.url,
                    postedAt = LocalDateTime.now(),
                    verified = false,
                    createdByMemberId = createdByMemberId,
                ),
            ).id
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청 경합: 유니크 제약이 최종 보장. 먼저 커밋한 공고를 재조회해 그 id 를 돌려준다.
            jobPostingRepository.findBySourceUidAndDeletedAtIsNull(sourceUid)?.id ?: throw e
        }
    }

    // URL → source_uid. 크롤러의 출처 UUID 와 섞이지 않도록 앱 전용 접두를 붙이고, 뒤에 URL 의 SHA-256 을 잇는다.
    // "lnk:"(4) + 60 = 64 로 source_uid VARCHAR(64) 안에 맞춘다. 동일 URL → 동일 uid → 멱등.
    private fun sourceUidOf(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.trim().toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        return SOURCE_UID_PREFIX + hex.take(SOURCE_UID_HASH_LENGTH)
    }

    companion object {
        private const val SOURCE_UID_PREFIX = "lnk:"
        private const val SOURCE_UID_HASH_LENGTH = 60
    }
}
