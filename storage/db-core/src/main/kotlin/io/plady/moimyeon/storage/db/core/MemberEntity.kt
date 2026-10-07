package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.core.enums.MemberStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime
import java.util.UUID

// 전체 컬럼 UPDATE 는 로그인 같은 다른 쓰기가 그사이 바뀐 수신 설정을 옛 값으로 덮는다.
@DynamicUpdate
@Entity
@Table(
    name = "member",
    uniqueConstraints = [UniqueConstraint(name = "uk_member_nickname", columnNames = ["nickname"])],
)
class MemberEntity(
    id: UUID,
    val email: String,
    nickname: String,
    status: MemberStatus,
    lastLoginAt: LocalDateTime,
    socialAccounts: List<SocialAccountEntity> = emptyList(),
    role: MemberRole = MemberRole.USER,
) : UuidBaseEntity(id) {
    var nickname: String = nickname
        protected set

    @Enumerated(EnumType.STRING)
    var status: MemberStatus = status
        protected set

    @Enumerated(EnumType.STRING)
    var role: MemberRole = role
        protected set

    var lastLoginAt: LocalDateTime = lastLoginAt
        protected set

    var isWebPushAllowed: Boolean = true
        protected set

    var isActivityEmailEnabled: Boolean = true
        protected set

    var isMarketingEmailAgreed: Boolean = false
        protected set

    var marketingEmailAgreedAt: LocalDateTime? = null
        protected set

    // cascade=ALL + orphanRemoval 이므로 컬렉션 조작이 곧 INSERT/DELETE 다. 외부에 노출하지 않는다.
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "member_id", nullable = false)
    private val socialAccounts: MutableList<SocialAccountEntity> = socialAccounts.toMutableList()

    fun socialAccounts(): List<SocialAccountEntity> = socialAccounts.toList()

    fun loggedIn(time: LocalDateTime) {
        this.lastLoginAt = time
    }

    fun changeNickname(nickname: String) {
        this.nickname = nickname
    }

    fun allowWebPush() {
        isWebPushAllowed = true
    }

    fun disallowWebPush() {
        isWebPushAllowed = false
    }

    fun changeActivityEmail(enabled: Boolean) {
        isActivityEmailEnabled = enabled
    }

    fun changeMarketingEmail(
        agreed: Boolean,
        time: LocalDateTime,
    ) {
        if (agreed && !isMarketingEmailAgreed) {
            marketingEmailAgreedAt = time
        }
        isMarketingEmailAgreed = agreed
    }

    fun canRestrict(): Boolean = status == MemberStatus.ACTIVE

    fun restrict() {
        check(canRestrict()) { "ACTIVE 회원만 제재할 수 있습니다. current=$status" }
        status = MemberStatus.RESTRICTED
    }

    fun canReactivate(): Boolean = status == MemberStatus.RESTRICTED

    fun reactivate() {
        check(canReactivate()) { "RESTRICTED 회원만 해제할 수 있습니다. current=$status" }
        status = MemberStatus.ACTIVE
    }
}
