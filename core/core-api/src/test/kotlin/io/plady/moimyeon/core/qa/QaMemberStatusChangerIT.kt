package io.plady.moimyeon.core.qa

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

class QaMemberStatusChangerIT(
    private val qaMemberStatusChanger: QaMemberStatusChanger,
    private val qaMemberCreator: QaMemberCreator,
    private val qaMemberEraser: QaMemberEraser,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private val createdIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        createdIds.forEach { qaMemberEraser.erase(it) }
    }

    @Test
    fun `QA 회원을 이용 제한으로 바꾸고 다시 되돌린다`() {
        val member = qaMemberCreator.create().also { createdIds += it.id }

        val restricted = qaMemberStatusChanger.change(member.id, MemberStatus.RESTRICTED)

        assertThat(restricted).isEqualTo(QaMemberStatus(member.id, before = MemberStatus.ACTIVE, status = MemberStatus.RESTRICTED))
        assertThat(memberRepository.findById(member.id).orElseThrow().status).isEqualTo(MemberStatus.RESTRICTED)

        qaMemberStatusChanger.change(member.id, MemberStatus.ACTIVE)

        assertThat(memberRepository.findById(member.id).orElseThrow().status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `이미 그 상태면 그대로 두고 성공한다`() {
        val member = qaMemberCreator.create().also { createdIds += it.id }

        val result = qaMemberStatusChanger.change(member.id, MemberStatus.ACTIVE)

        assertThat(result).isEqualTo(QaMemberStatus(member.id, before = MemberStatus.ACTIVE, status = MemberStatus.ACTIVE))
    }
}
