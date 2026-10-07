package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

// 공고-직무 매핑(schema.sql job_posting_role). 한 공고가 여러 직무에 걸릴 수 있으며,
// 공고 목록 응답은 이 매핑에서 대표 직무 하나를 골라 직무 셀렉트 자동 채움 힌트로 쓴다(§4.1).
// 순수 조인 테이블이라 타임스탬프·소프트삭제 컬럼이 없어 BaseEntity 를 상속하지 않는다.
@Entity
@Table(
    name = "job_posting_role",
    uniqueConstraints = [UniqueConstraint(name = "uk_job_posting_role_posting_role", columnNames = ["job_posting_id", "job_role_id"])],
)
class JobPostingRoleEntity(
    val jobPostingId: Long,
    val jobRoleId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
