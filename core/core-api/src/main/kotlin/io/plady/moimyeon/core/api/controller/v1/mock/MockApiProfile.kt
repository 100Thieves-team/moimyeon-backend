package io.plady.moimyeon.core.api.controller.v1.mock

import org.springframework.context.annotation.Profile

// 모킹 API 활성화 범위: local·개발 환경에서만 빈으로 등록되고 운영(live)에는 노출되지 않는다.
// 새 기능의 API 를 모킹으로 먼저 배포할 때 컨트롤러에 붙여 재사용한다.
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Profile("local", "local-dev", "dev")
annotation class MockApiProfile
