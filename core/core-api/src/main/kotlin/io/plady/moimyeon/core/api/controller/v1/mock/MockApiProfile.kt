package io.plady.moimyeon.core.api.controller.v1.mock

import org.springframework.context.annotation.Profile

// TODO(MOI-316): 모킹 API 는 운영(live)에 노출하지 않는다. 실 구현 시 제거한다.
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Profile("local", "local-dev", "dev")
annotation class MockApiProfile
