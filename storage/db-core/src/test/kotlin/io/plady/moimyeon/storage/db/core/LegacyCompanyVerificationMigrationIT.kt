package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.storage.db.CoreDbContextTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.sql.DataSource

@Transactional
class LegacyCompanyVerificationMigrationIT(
    private val companyRepository: CompanyRepository,
    private val dataSource: DataSource,
    private val entityManager: EntityManager,
) : CoreDbContextTest() {
    @Test
    fun `기존 크롤러 회사만 검증 완료 상태로 전환한다`() {
        val catalogCompany = companyRepository.saveAndFlush(
            CompanyEntity(corpCode = "12345678", nameKr = "기존 카탈로그 회사", nameNormalized = "기존카탈로그회사"),
        )
        val memberCompany = companyRepository.saveAndFlush(
            CompanyEntity(
                corpCode = null,
                nameKr = "회원 등록 회사",
                nameNormalized = "회원등록회사",
                createdByMemberId = UUID.randomUUID(),
            ),
        )

        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V7__verify_legacy_catalog_companies.sql"),
        ).execute(dataSource)
        entityManager.clear()

        assertThat(companyRepository.findById(catalogCompany.id).orElseThrow().verified).isTrue()
        assertThat(companyRepository.findById(memberCompany.id).orElseThrow().verified).isFalse()
    }
}
