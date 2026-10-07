package io.plady.moimyeon.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface MemberProfileRepository : JpaRepository<MemberProfileEntity, UUID> {
    fun findByMemberIdAndDeletedAtIsNull(memberId: UUID): MemberProfileEntity?

    fun findByMemberIdInAndDeletedAtIsNull(memberIds: Collection<UUID>): List<MemberProfileEntity>

    fun existsByMemberIdAndDeletedAtIsNull(memberId: UUID): Boolean

    // 쓰기 경로 전용. 프로필 행을 잠가 같은 회원의 동시 저장을 직렬화한다.
    // 관심 회사·직무 교체가 "현재 목록을 읽고 차집합을 적용"하는 방식이라, 잠그지 않으면
    // 두 요청이 서로의 결과를 덮어쓴다(둘 다 {A,B}를 보고 하나는 A만, 하나는 B만 남기면 둘 다 사라짐).
    // 삭제된 행도 대상이다 — 되살리기 경로가 그 행을 잡아야 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByMemberId(memberId: UUID): MemberProfileEntity?
}
