package io.plady.moimyeon.core.domain.catalog

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.storage.db.core.JobGroupEntity
import io.plady.moimyeon.storage.db.core.JobGroupRepository
import io.plady.moimyeon.storage.db.core.JobRoleEntity
import io.plady.moimyeon.storage.db.core.JobRoleRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Transactional
class JobCatalogFinderIT(
    private val jobCatalogFinder: JobCatalogFinder,
    private val jobGroupRepository: JobGroupRepository,
    private val jobRoleRepository: JobRoleRepository,
) : ContextTest() {
    @Test
    fun `유효한 직군과 직무를 정렬하고 직군별로 묶어 반환한다`() {
        val firstGroup = saveGroup("TEST_JOB_GROUP_A", 30_000)
        saveRole(firstGroup.id, "TEST_JOB_ROLE_A2", 2)
        saveRole(firstGroup.id, "TEST_JOB_ROLE_A1", 1)
        saveGroup("TEST_JOB_GROUP_B", 30_001)

        val catalog = jobCatalogFinder.getJobCatalog()

        assertThat(catalog.takeLast(2).map { it.code }).containsExactly("TEST_JOB_GROUP_A", "TEST_JOB_GROUP_B")
        assertThat(catalog.first { it.id == firstGroup.id }.roles.map { it.code })
            .containsExactly("TEST_JOB_ROLE_A1", "TEST_JOB_ROLE_A2")
    }

    @Test
    fun `폐기된 직군과 직무는 카탈로그에서 제외한다`() {
        val now = LocalDateTime.of(2026, 8, 1, 0, 0)
        val group = saveGroup("TEST_DELETED_JOB_GROUP", 30_000)
        val role = saveRole(group.id, "TEST_DELETED_JOB_ROLE", 1)
        role.delete(now)
        jobRoleRepository.flush()

        assertThat(jobCatalogFinder.getJobCatalog().first { it.id == group.id }.roles).isEmpty()

        group.delete(now)
        jobGroupRepository.flush()

        assertThat(jobCatalogFinder.getJobCatalog().map { it.id }).doesNotContain(group.id)
    }

    @Test
    fun `직무명으로 유효 직무를 상위 직군과 함께 검색한다`() {
        val group = saveGroup("TEST_SEARCH_GROUP", 30_000)
        val backend = jobRoleRepository.saveAndFlush(JobRoleEntity(group.id, "TEST_SEARCH_BE", "테스트 백엔드 개발", 1))
        jobRoleRepository.saveAndFlush(JobRoleEntity(group.id, "TEST_SEARCH_FE", "테스트 프론트 개발", 2))
        val deleted = jobRoleRepository.saveAndFlush(JobRoleEntity(group.id, "TEST_SEARCH_DEL", "테스트 백엔드 폐기", 3))
        deleted.delete(LocalDateTime.of(2026, 8, 1, 0, 0))
        jobRoleRepository.flush()

        val found = jobCatalogFinder.searchJobRoles("테스트 백엔드")

        assertThat(found.map { it.id }).containsExactly(backend.id)
        assertThat(found.first().displayName).isEqualTo("테스트 백엔드 개발")
        assertThat(found.first().groupCode).isEqualTo("TEST_SEARCH_GROUP")
    }

    @Test
    fun `폐기된 직군의 직무는 검색에서 제외한다`() {
        val group = saveGroup("TEST_SEARCH_DEL_GROUP", 30_000)
        jobRoleRepository.saveAndFlush(JobRoleEntity(group.id, "TEST_SEARCH_ORPHAN", "테스트 고아 직무", 1))
        group.delete(LocalDateTime.of(2026, 8, 1, 0, 0))
        jobGroupRepository.flush()

        assertThat(jobCatalogFinder.searchJobRoles("테스트 고아")).isEmpty()
    }

    private fun saveGroup(code: String, sortOrder: Short): JobGroupEntity {
        return jobGroupRepository.saveAndFlush(JobGroupEntity(code, code, sortOrder))
    }

    private fun saveRole(jobGroupId: Long, code: String, sortOrder: Short): JobRoleEntity {
        return jobRoleRepository.saveAndFlush(JobRoleEntity(jobGroupId, code, code, sortOrder))
    }
}
