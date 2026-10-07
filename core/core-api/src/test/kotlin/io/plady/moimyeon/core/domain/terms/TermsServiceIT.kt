package io.plady.moimyeon.core.domain.terms

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.enums.TermsType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import io.plady.moimyeon.storage.db.core.TermsEntity
import io.plady.moimyeon.storage.db.core.TermsRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Transactional
class TermsServiceIT(
    private val termsRepository: TermsRepository,
    private val termsAgreementRepository: TermsAgreementRepository,
) : ContextTest() {
    private val effectiveAt = LocalDateTime.of(2026, 10, 8, 0, 0)
    private val clock = Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ZoneOffset.UTC)
    private val termsService by lazy { TermsService(TermsFinder(termsRepository, clock)) }
    private val termsAgreementManager by lazy { TermsAgreementManager(termsRepository, termsAgreementRepository, clock) }
    private val termsAgreementFinder by lazy { TermsAgreementFinder(termsRepository, termsAgreementRepository, clock) }

    @BeforeEach
    fun setUp() {
        termsAgreementRepository.deleteAll()
        termsAgreementRepository.flush()
        termsRepository.deleteAll()
        termsRepository.flush()
        persistTerms(TermsType.SERVICE, "v1.0", effectiveAt.minusDays(1))
        persistTerms(TermsType.PRIVACY, "v1.0", effectiveAt.minusDays(1))
    }

    @Test
    fun `UTC 서버에서도 한국 시행시각 정각부터 종류별 최신 문서만 조회한다`() {
        val latest = persistTerms(TermsType.SERVICE, "v1.1", effectiveAt)
        persistTerms(TermsType.SERVICE, "v1.2", effectiveAt.plusSeconds(1))
        persistTerms(TermsType.PRIVACY, "v1.1", effectiveAt, TermsStatus.DRAFT)
        val deleted = persistTerms(TermsType.PRIVACY, "v1.2", effectiveAt)
        deleted.delete(effectiveAt)
        termsRepository.flush()

        val terms = termsService.getActiveTerms()

        assertThat(terms.map { it.type }).containsExactly(TermsType.SERVICE, TermsType.PRIVACY)
        assertThat(terms.map { it.id }).contains(latest.id).doesNotContain(deleted.id)
        assertThat(terms.map { it.version }).containsExactly("v1.1", "v1.0")
    }

    @Test
    fun `시행 직전에는 새 버전 대신 기존 버전을 유지한다`() {
        persistTerms(TermsType.SERVICE, "v1.1", effectiveAt)
        val before = Clock.offset(clock, java.time.Duration.ofNanos(-1))
        val beforeService = TermsService(TermsFinder(termsRepository, before))

        assertThat(beforeService.getActiveTerms().map { it.version }).containsExactly("v1.0", "v1.0")
    }

    @Test
    fun `가입 자동 동의와 필수 동의 판정은 목록과 같은 현재 버전을 사용한다`() {
        persistTerms(TermsType.SERVICE, "v1.1", effectiveAt)
        persistTerms(TermsType.PRIVACY, "v1.1", effectiveAt.plusDays(1))
        val memberId = UUID.randomUUID()
        assertThat(termsAgreementFinder.hasAgreedAllRequiredActive(memberId)).isFalse()

        termsAgreementManager.agreeRequired(memberId, LocalDateTime.of(2026, 10, 7, 15, 0))

        assertThat(termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId).map { it.termsId })
            .containsExactlyInAnyOrderElementsOf(termsService.getActiveTerms().map { it.id })
        assertThat(termsAgreementFinder.hasAgreedAllRequiredActive(memberId)).isTrue()
    }

    @Test
    fun `최신 버전이 선택 항목이면 과거 필수 버전에 자동 동의하지 않는다`() {
        val optional = persistTerms(TermsType.PRIVACY, "v1.1", effectiveAt, required = false)
        val memberId = UUID.randomUUID()

        termsAgreementManager.agreeRequired(memberId, effectiveAt)

        val agreements = termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId)
        assertThat(agreements).hasSize(1)
        assertThat(agreements.map { it.termsId }).doesNotContain(optional.id)
        assertThat(termsAgreementFinder.hasAgreedAllRequiredActive(memberId)).isTrue()
    }

    @Test
    fun `같은 시행시각의 활성 버전이 중복되면 E1203으로 임의 선택을 막는다`() {
        persistTerms(TermsType.SERVICE, "v1.1", effectiveAt)
        persistTerms(TermsType.SERVICE, "v1.2", effectiveAt)

        assertThatThrownBy { termsService.getActiveTerms() }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.TERMS_UNAVAILABLE)
            }
    }

    @Test
    fun `필수 문서 종류가 비어 있으면 자동 동의 기록 없이 E1203을 반환한다`() {
        termsRepository.deleteAll(termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.ACTIVE).filter { it.type == TermsType.PRIVACY })
        val memberId = UUID.randomUUID()

        assertThatThrownBy { termsAgreementManager.agreeRequired(memberId, effectiveAt) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.TERMS_UNAVAILABLE)
            }
        assertThat(termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId)).isEmpty()
    }

    @Test
    fun `시행된 과거 버전도 ID로 전문을 조회한다`() {
        val previous = persistTerms(TermsType.SERVICE, "archive", effectiveAt.minusMonths(1), TermsStatus.DEPRECATED)

        assertThat(termsService.getTerms(previous.id).content).isEqualTo("검토 본문\n두 번째 줄")
    }

    @Test
    fun `초안과 미시행과 삭제된 문서는 존재하지 않는 ID와 같은 E1202를 반환한다`() {
        val draft = persistTerms(TermsType.SERVICE, "draft", effectiveAt, TermsStatus.DRAFT)
        val future = persistTerms(TermsType.SERVICE, "future", effectiveAt.plusDays(1))
        val unpublishedArchive = persistTerms(TermsType.SERVICE, "future-archive", effectiveAt.plusDays(1), TermsStatus.DEPRECATED)
        val deleted = persistTerms(TermsType.SERVICE, "deleted", effectiveAt)
        deleted.delete(effectiveAt)
        termsRepository.flush()

        listOf(draft.id, future.id, unpublishedArchive.id, deleted.id, UUID.randomUUID()).forEach { id ->
            assertThatThrownBy { termsService.getTerms(id) }
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.TERMS_NOT_FOUND)
                }
        }
    }

    private fun persistTerms(
        type: TermsType,
        version: String,
        effectiveFrom: LocalDateTime,
        status: TermsStatus = TermsStatus.ACTIVE,
        required: Boolean = true,
    ): TermsEntity = termsRepository.saveAndFlush(
        TermsEntity(
            id = UUID.randomUUID(),
            type = type,
            version = version,
            title = "문서",
            content = "검토 본문\n두 번째 줄",
            required = required,
            effectiveFrom = effectiveFrom,
            status = status,
        ),
    )
}
