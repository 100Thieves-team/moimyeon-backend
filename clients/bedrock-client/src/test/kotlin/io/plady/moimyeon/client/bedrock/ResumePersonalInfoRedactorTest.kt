package io.plady.moimyeon.client.bedrock

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResumePersonalInfoRedactorTest {
    @Test
    fun `연락처와 고유식별정보 패턴을 마스킹한다`() {
        val text = """
            이메일: applicant@example.com
            applicant@example.net / 010 - 1234 - 5678
            02-1234-5678 / 031-123-4567 / +82 10 9876 5432
            주민등록번호: 990101 - 1234567
            경력 3년의 Kotlin 백엔드 개발자
        """.trimIndent()

        val redacted = ResumePersonalInfoRedactor.redact(text)

        assertThat(redacted)
            .doesNotContain(
                "applicant@example.com",
                "applicant@example.net",
                "010 - 1234 - 5678",
                "02-1234-5678",
                "031-123-4567",
                "+82 10 9876 5432",
                "990101 - 1234567",
            )
            .contains("[REDACTED]", "경력 3년의 Kotlin 백엔드 개발자")
    }

    @Test
    fun `주소와 학번은 라벨이 있는 줄의 값을 마스킹한다`() {
        val text = """
            주소: 서울시 테스트구 가상로 123
            학번: TEST-2026-7391
            주소 기반 검색 서비스를 개발했습니다.
        """.trimIndent()

        val redacted = ResumePersonalInfoRedactor.redact(text)

        assertThat(redacted)
            .contains("주소: [REDACTED]", "학번: [REDACTED]", "주소 기반 검색 서비스를 개발했습니다.")
            .doesNotContain("서울시 테스트구 가상로 123", "TEST-2026-7391")
    }

    @Test
    fun `라벨 없는 도로명 주소를 마스킹하되 경력 날짜와 성과 숫자는 보존한다`() {
        val text = """
            서울시 강남구 테헤란로 123에서 거주
            경기도 성남시 분당구 판교역로 123에서 근무
            20200101 / 2020-01-01 / 처리량 20191231
        """.trimIndent()

        val redacted = ResumePersonalInfoRedactor.redact(text)

        assertThat(redacted)
            .contains("[REDACTED]")
            .contains("20200101", "2020-01-01", "20191231")
            .doesNotContain("서울시 강남구 테헤란로 123", "경기도 성남시 분당구 판교역로 123")
    }
}
