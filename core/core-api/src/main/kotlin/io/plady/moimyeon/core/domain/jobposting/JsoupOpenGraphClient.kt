package io.plady.moimyeon.core.domain.jobposting

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.IDN
import java.net.InetAddress
import java.net.URI

// OG 메타데이터를 Jsoup 으로 fetch·파싱한다. 사용자가 준 임의 URL 을 서버가 대신 여는 만큼,
// (1) 사설·루프백·링크로컬(클라우드 메타데이터 169.254.169.254 포함) 대상은 SSRF 방어로 fetch 전 차단하고,
// (2) 3초 타임아웃·본문 크기 상한으로 외부 사이트가 우리 응답을 늘어지게 하지 못하게 막으며,
// (3) 어떤 실패(차단·타임아웃·봇 차단 4xx·비HTML·파싱 오류)도 예외로 전파하지 않고 빈 메타로 흡수한다.
//
// 한계: fetch 전 IP 검사와 Jsoup 의 실제 연결 사이에는 DNS 재바인딩 창이 남는다(TOCTOU). 완전한 차단은
// 연결 시점에 해석된 IP 를 고정해야 하며, 그건 후속 과제로 둔다. 여기서는 명백한 내부 대상만 막는다.
@Component
class JsoupOpenGraphClient : OpenGraphClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetch(url: String): LinkMetadata {
        val empty = LinkMetadata(postingName = null, imageUrl = null, description = null, sourceUrl = url)
        if (!isFetchable(url)) {
            log.info("OG fetch 차단(SSRF·스킴) url={}", url)
            return empty
        }
        return try {
            val document = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MILLIS)
                .maxBodySize(MAX_BODY_BYTES)
                .followRedirects(true)
                .ignoreHttpErrors(false) // 4xx/5xx(봇 차단 등)는 예외 → 아래 catch 에서 빈 메타
                .get()
            LinkMetadata(
                postingName = document.metaContent("og:title") ?: document.title().trimToNull(),
                imageUrl = document.metaContent("og:image", asUrl = true),
                description = document.metaContent("og:description"),
                sourceUrl = document.metaContent("og:url", asUrl = true) ?: url,
            )
        } catch (e: Exception) {
            log.info("OG fetch 실패 url={} cause={}", url, e.message)
            empty
        }
    }

    // og:* 는 표준상 property 지만 name 으로 쓰는 사이트도 있어 둘 다 본다. 빈 content 는 없는 것으로 취급한다.
    // asUrl=true 면 상대·프로토콜상대 경로(예: LINE "/static/..", 사람인 "//www..")를 페이지 base 기준 절대 URL 로 바꾼다 —
    // 프론트 미리보기가 그대로 쓸 수 있어야 하기 때문이다. 해석 실패 시엔 원문을 그대로 둔다.
    private fun Document.metaContent(property: String, asUrl: Boolean = false): String? {
        val element = selectFirst("meta[property=$property]") ?: selectFirst("meta[name=$property]") ?: return null
        val content = if (asUrl) element.absUrl("content").ifBlank { element.attr("content") } else element.attr("content")
        return content.trimToNull()
    }

    private fun String.trimToNull(): String? = trim().ifBlank { null }

    // fetch 가능한 대상인지 검사한다: http/https 만 허용하고, 호스트가 내부 대역으로 해석되면 막는다.
    private fun isFetchable(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        return try {
            val ascii = IDN.toASCII(host)
            val addresses = InetAddress.getAllByName(ascii)
            addresses.isNotEmpty() && addresses.none { it.isBlockedTarget() }
        } catch (e: Exception) {
            false
        }
    }

    // 내부·특수 용도 대역 차단. JDK 표준 판정 + IPv6 ULA(fc00::/7)·IPv4 CGNAT(100.64.0.0/10) 보강.
    private fun InetAddress.isBlockedTarget(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
            return true
        }
        val bytes = address
        if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return true // IPv6 ULA fc00::/7
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            if (first == 100 && second in 64..127) return true // CGNAT 100.64.0.0/10
        }
        return false
    }

    companion object {
        // 채용 사이트가 헤드리스 요청을 막는 경우가 많아 일반 브라우저 UA 로 위장한다(그래도 막히면 빈 메타 폴백).
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0 Safari/537.36"
        private const val TIMEOUT_MILLIS = 3_000
        private const val MAX_BODY_BYTES = 1 * 1024 * 1024 // 1MB — 헤드의 OG 태그만 필요해 본문 전체는 받지 않는다
    }
}
