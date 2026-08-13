package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import java.util.UUID

// 룸 생성은 방장 회원 행을 잠그면서 실재·ACTIVE 를 함께 본다(MOI-331). 그래서 생성 경로를 태우는
// IT 는 방장 회원 행이 있어야 한다. 같은 블록을 IT 마다 복제하지 않으려고 한자리에 둔다.
//
// tag 는 회원마다 달라야 한다 — 닉네임과 소셜 계정에 유니크가 걸려 있어 겹치면 픽스처끼리 충돌한다.
internal fun activeMember(id: UUID, tag: String): MemberEntity = MemberEntity(
    id = id,
    email = "$tag@example.com",
    nickname = tag,
    status = MemberStatus.ACTIVE,
    lastLoginAt = FIXED_NOW,
    socialAccounts = listOf(
        SocialAccountEntity(
            provider = SocialLoginProvider.GOOGLE,
            providerId = tag,
            linkedEmail = "$tag@example.com",
        ),
    ),
)
