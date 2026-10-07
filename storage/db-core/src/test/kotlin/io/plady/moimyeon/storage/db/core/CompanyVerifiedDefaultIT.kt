package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.storage.db.CoreDbContextTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
class CompanyVerifiedDefaultIT(
    private val entityManager: EntityManager,
) : CoreDbContextTest() {
    @Test
    fun `크롤러가 넣은 회사만 검증 완료 상태로 들어간다`() {
        // 크롤러는 verified 를 지정하지 않고 넣는다 — 그때 컬럼 기본값이 적용된다
        entityManager.createNativeQuery(
            "INSERT INTO company (name_kr, name_normalized, created_at, updated_at) " +
                "VALUES ('크롤러 적재 회사', '크롤러적재회사', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        ).executeUpdate()
        // 앱은 JPA 로 넣으므로 값이 항상 명시된다
        entityManager.persist(
            CompanyEntity(
                corpCode = null,
                nameKr = "회원 등록 회사",
                nameNormalized = "회원등록회사",
                createdByMemberId = UUID.randomUUID(),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val verified = entityManager
            .createQuery("SELECT c FROM CompanyEntity c WHERE c.nameKr IN :names", CompanyEntity::class.java)
            .setParameter("names", listOf("크롤러 적재 회사", "회원 등록 회사"))
            .resultList
            .associate { it.nameKr to it.verified }

        assertThat(verified["크롤러 적재 회사"]).isTrue()
        assertThat(verified["회원 등록 회사"]).isFalse()
    }
}
