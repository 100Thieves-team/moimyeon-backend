package io.plady.moimyeon.core.api.controller.v1

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ProfileContractRegressionTest {
    @Disabled("MOI-378 구현 예정")
    @Test
    fun `내 프로필 조회는 복수 관심 직무와 관심 회사를 유지하고 면접 단계를 노출하지 않는다`() {
    }

    @Disabled("MOI-378 구현 예정")
    @Test
    fun `내 프로필 전체 교체는 복수 관심 직무와 관심 회사를 유지하고 면접 단계를 받지 않는다`() {
    }

    @Disabled("MOI-378 구현 예정")
    @Test
    fun `공개 프로필은 공개 필드와 항상 존재하는 신뢰 객체만 반환한다`() {
    }

    @Disabled("MOI-378 구현 예정")
    @Test
    fun `공개 프로필은 인증 없이 조회할 수 있다`() {
    }

    @Disabled("MOI-378 구현 예정")
    @Test
    fun `존재하지 않거나 탈퇴한 회원의 공개 프로필은 E1006 을 반환한다`() {
    }

    @Disabled("MOI-378 구현 예정")
    @Test
    fun `회원 식별자가 UUID 형식이 아니면 E400 을 반환한다`() {
    }
}
