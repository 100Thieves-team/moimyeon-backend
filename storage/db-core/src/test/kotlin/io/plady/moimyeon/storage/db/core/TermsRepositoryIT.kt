package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.enums.TermsType
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class TermsRepositoryIT(
    val termsRepository: TermsRepository,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun persistTerms(version: String, status: TermsStatus): TermsEntity {
        return termsRepository.saveAndFlush(
            TermsEntity(
                id = UUID.randomUUID(),
                type = TermsType.SERVICE,
                version = version,
                title = "이용약관",
                content = "본문",
                required = true,
                effectiveFrom = now,
                status = status,
            ),
        )
    }

    @Test
    fun `소프트 삭제된 약관은 유효 약관 조회에서 빠진다`() {
        // given
        val alive = persistTerms("it-2.0", TermsStatus.ACTIVE)
        val deleted = persistTerms("it-2.1", TermsStatus.ACTIVE)

        // when
        deleted.delete(now)
        termsRepository.flush()

        // then
        val found = termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.ACTIVE).map { it.id }
        assertThat(found).contains(alive.id)
        assertThat(found).doesNotContain(deleted.id)
    }

    @Test
    fun `status 와 deletedAt 은 서로 다른 사실이라 DEPRECATED 약관도 살아있는 행으로 남는다`() {
        // given
        val deprecated = persistTerms("it-2.2", TermsStatus.DEPRECATED)

        // then
        assertThat(deprecated.isDeleted()).isFalse()
        assertThat(termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.DEPRECATED).map { it.id })
            .contains(deprecated.id)
    }
}
