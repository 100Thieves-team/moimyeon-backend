package io.plady.moimyeon.core.domain.jobposting

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// SSRF·스킴 차단은 fetch 전에 걸리므로 네트워크를 타지 않는다(리터럴 IP·loopback 은 DNS 조회 없이 해석된다).
// 실제 외부 사이트 호출 결과는 네트워크 의존이라 여기서 검증하지 않는다(BE-03 라이브 점검 문서 참고).
class JsoupOpenGraphClientTest {
    private val client = JsoupOpenGraphClient()

    @Test
    fun `내부·특수 대역 대상은 fetch 하지 않고 빈 메타를 돌려준다`() {
        val internalUrls = listOf(
            "http://169.254.169.254/latest/meta-data/", // 클라우드 메타데이터(링크로컬)
            "http://127.0.0.1/", // 루프백
            "http://localhost:8080/actuator", // 루프백(호스트명)
            "http://10.0.0.5/internal", // 사설 10/8
            "http://192.168.0.1/admin", // 사설 192.168/16
            "http://172.16.0.1/", // 사설 172.16/12
            "http://100.64.0.1/", // CGNAT 100.64/10
            "https://[::1]/", // IPv6 루프백
        )

        internalUrls.forEach { url ->
            val result = client.fetch(url)
            assertThat(result.postingName).describedAs(url).isNull()
            assertThat(result.imageUrl).describedAs(url).isNull()
            assertThat(result.description).describedAs(url).isNull()
            assertThat(result.sourceUrl).describedAs(url).isEqualTo(url)
        }
    }

    @Test
    fun `http·https 가 아니거나 형식이 깨진 url 은 빈 메타를 돌려준다`() {
        val invalidUrls = listOf(
            "ftp://company.example.com/careers", // http/https 아님
            "file:///etc/passwd", // 파일 스킴
            "javascript:alert(1)", // 스킴 악용
            "not-a-valid-url", // 파싱 불가
            "http://", // 호스트 없음
            "   ", // 공백
        )

        invalidUrls.forEach { url ->
            val result = client.fetch(url)
            assertThat(result.postingName).describedAs(url).isNull()
            assertThat(result.sourceUrl).describedAs(url).isEqualTo(url)
        }
    }
}
