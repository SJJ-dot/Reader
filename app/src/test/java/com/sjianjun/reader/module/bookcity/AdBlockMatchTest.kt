package com.sjianjun.reader.module.bookcity

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证广告拦截匹配逻辑（纯 IP 站点支持、按端口匹配）。
 * 直接调用文件顶层 internal 纯函数 adBlockRuleMatches，避免触碰 AdBlock 单例的 LiveData（JVM 测试无 Android Looper）。
 *
 * 规则：
 * - 纯 IP 当作顶级域名匹配（topPrivateDomain() 对 IP 返回 null，退化为 host）；
 * - 规则带 :端口 则仅匹配该端口，未写端口匹配该 host 所有端口；
 * - URL 未写端口时取其协议默认端口（http=80 / https=443）。
 */
class AdBlockMatchTest {

    private fun url(s: String): HttpUrl = s.toHttpUrlOrNull()!!

    @Test
    fun `纯IP 域名规则拦截其所有端口`() {
        val e = HostStr("1.2.3.4").apply { mode = BLOCK_MODE_DOMAIN }
        val u = url("http://1.2.3.4/")
        assertTrue(adBlockRuleMatches(e, u, u.topPrivateDomain(), "http://1.2.3.4/"))
        assertTrue(adBlockRuleMatches(e, url("http://1.2.3.4:8080/path"), "1.2.3.4", "http://1.2.3.4:8080/path"))
        assertTrue(adBlockRuleMatches(e, url("https://1.2.3.4/"), "1.2.3.4", "https://1.2.3.4/"))
        val other = HostStr("9.9.9.9").apply { mode = BLOCK_MODE_DOMAIN }
        assertFalse(adBlockRuleMatches(other, u, u.topPrivateDomain(), "http://9.9.9.9/"))
    }

    @Test
    fun `指定端口的域名规则仅匹配该端口`() {
        val e = HostStr("example.com:80").apply { mode = BLOCK_MODE_DOMAIN }
        assertTrue(adBlockRuleMatches(e, url("http://example.com/"), "example.com", "http://example.com/")) // 默认 80
        assertTrue(adBlockRuleMatches(e, url("http://example.com:80/x"), "example.com", "http://example.com:80/x"))
        assertFalse(adBlockRuleMatches(e, url("https://example.com/"), "example.com", "https://example.com/")) // 443 放行
        assertFalse(adBlockRuleMatches(e, url("http://example.com:8080/x"), "example.com", "http://example.com:8080/x"))
    }

    @Test
    fun `不带端口的域名规则匹配所有端口`() {
        val e = HostStr("example.com").apply { mode = BLOCK_MODE_DOMAIN }
        assertTrue(adBlockRuleMatches(e, url("http://example.com/"), "example.com", "http://example.com/"))
        assertTrue(adBlockRuleMatches(e, url("https://example.com/"), "example.com", "https://example.com/"))
        assertTrue(adBlockRuleMatches(e, url("http://example.com:8080/"), "example.com", "http://example.com:8080/"))
    }

    @Test
    fun `子域名加端口规则仅匹配该 host 与端口`() {
        val e = HostStr("ads.example.com:8080").apply { mode = BLOCK_MODE_SUBDOMAIN }
        assertTrue(adBlockRuleMatches(e, url("http://ads.example.com:8080/x"), "example.com", "http://ads.example.com:8080/x"))
        assertFalse(adBlockRuleMatches(e, url("http://ads.example.com/x"), "example.com", "http://ads.example.com/x")) // 默认 80
        assertFalse(adBlockRuleMatches(e, url("https://ads.example.com/"), "example.com", "https://ads.example.com/")) // 默认 443
    }

    @Test
    fun `精确URL规则按字符串匹配`() {
        val e = HostStr("http://example.com/ad.js").apply { mode = BLOCK_MODE_URL }
        assertTrue(adBlockRuleMatches(e, url("http://example.com/ad.js"), "example.com", "http://example.com/ad.js"))
        assertFalse(adBlockRuleMatches(e, url("http://example.com/ad.js?x=1"), "example.com", "http://example.com/ad.js?x=1"))
    }

    @Test
    fun `三级规则值 子域名含协议与端口`() {
        assertEquals("ads.example.com:80", adBlockHostPortOf("http://ads.example.com/a.js"))
        assertEquals("1.2.3.4:8080", adBlockHostPortOf("http://1.2.3.4:8080/x"))
        assertEquals("http://ads.example.com:80", adBlockSubdomainOf("http://ads.example.com/a.js"))
        assertEquals("https://ads.example.com:443", adBlockSubdomainOf("https://ads.example.com/a.js"))
        assertEquals("https://ads.example.com:8443", adBlockSubdomainOf("https://ads.example.com:8443/a.js"))
    }

    @Test
    fun `子域名规则 显式端口相同但协议不同也是两条`() {
        val a = adBlockSubdomainOf("http://ads.example.com:12345/a.js")
        val b = adBlockSubdomainOf("http://ads.example.com:12346/a.js")
        val c = adBlockSubdomainOf("https://ads.example.com:12345/a.js")
        assertEquals("http://ads.example.com:12345", a)
        assertEquals("http://ads.example.com:12346", b)
        assertEquals("https://ads.example.com:12345", c)
        assertTrue(setOf(a, b, c).size == 3)

        val ruleA = HostStr(a).apply { mode = BLOCK_MODE_SUBDOMAIN }
        fun matches(raw: String) = adBlockRuleMatches(ruleA, url(raw), "example.com", raw)
        assertTrue(matches("http://ads.example.com:12345/x.js"))
        assertFalse(matches("http://ads.example.com:12346/x.js"))   // 同协议、不同端口
        assertFalse(matches("https://ads.example.com:12345/x.js"))  // 同端口、不同协议
        assertFalse(matches("http://other.example.com:12345/x.js")) // 不同 host
    }

    @Test
    fun `旧格式子域名规则不带协议 仍匹配该端口的所有协议`() {
        val legacy = HostStr("ads.example.com:12345").apply { mode = BLOCK_MODE_SUBDOMAIN }
        fun matches(raw: String) = adBlockRuleMatches(legacy, url(raw), "example.com", raw)
        assertTrue(matches("http://ads.example.com:12345/x"))
        assertTrue(matches("https://ads.example.com:12345/x"))
        assertFalse(matches("http://ads.example.com:12346/x"))
    }

    @Test
    fun `三级规则 顶级域名不区分协议端口 子域名区分 URL精确`() {
        // 顶级域名规则值不含协议/端口。纯 JVM 单测没有 Android context，
        // 无法加载 OkHttp 公共后缀库，故 top 参数按现有测试惯例硬编码传入。
        val top = HostStr("example.com").apply { mode = BLOCK_MODE_DOMAIN }
        val sub = HostStr(adBlockSubdomainOf("https://ads.example.com/track.js"))
            .apply { mode = BLOCK_MODE_SUBDOMAIN }
        val exact = HostStr("https://ads.example.com/track.js").apply { mode = BLOCK_MODE_URL }

        val httpsSub = "https://ads.example.com/track.js"
        val httpSub = "http://ads.example.com/track.js"
        val otherUrl = "https://ads.example.com/other.js"
        val otherHost = "https://other.example.com/track.js"
        fun matches(e: HostStr, raw: String) = adBlockRuleMatches(e, url(raw), "example.com", raw)

        // 顶级域名级：http / https / 显式端口全部命中
        assertTrue(matches(top, "http://example.com/a"))
        assertTrue(matches(top, httpsSub))
        assertTrue(matches(top, "https://example.com:8443/x"))

        // 子域名级：协议 + host + 端口完全一致才命中
        assertTrue(matches(sub, httpsSub))
        assertFalse(matches(sub, httpSub))
        assertFalse(matches(sub, otherHost))

        // 精确URL级：同域其它 URL 与同 host 的 http 版本都不命中（防止误标整个域名下所有请求）
        assertTrue(matches(exact, httpsSub))
        assertFalse(matches(exact, otherUrl))
        assertFalse(matches(exact, httpSub))
    }

    @Test
    fun `黑白名单按钮互斥`() {
        // (黑名单按钮可见, 白名单按钮可见)
        assertEquals(true to true, adBlockRuleButtonsVisible(false, false))  // 两个「+」都可用
        assertEquals(true to false, adBlockRuleButtonsVisible(true, false))  // 已加黑名单：只能先「-黑名单」
        assertEquals(false to true, adBlockRuleButtonsVisible(false, true))  // 已加白名单：只能先「-白名单」
        assertEquals(true to true, adBlockRuleButtonsVisible(true, true))    // 历史脏数据：保留两个「-」以便清理
    }

    @Test
    fun `拦截顶级域名或子域名后 放行子域名或URL 按层级优先级生效`() {
        val raw = "https://ads.example.com/ad.js"
        val topBlack = HostStr("example.com").apply { mode = BLOCK_MODE_DOMAIN }
        val subBlack = HostStr("https://ads.example.com:443").apply { mode = BLOCK_MODE_SUBDOMAIN }
        val subWhite = HostStr("https://ads.example.com:443").apply { mode = BLOCK_MODE_SUBDOMAIN }
        val urlWhite = HostStr(raw).apply { mode = BLOCK_MODE_URL }
        // 纯 JVM 单测无公共后缀库，top 参数按惯例硬编码
        fun blocked(black: List<HostStr>, white: List<HostStr>) =
            adBlockIsBlocked(raw, black, white, "example.com")

        // 只有黑名单规则：命中
        assertTrue(blocked(listOf(topBlack), emptyList()))
        assertTrue(blocked(listOf(subBlack), emptyList()))

        // 拦顶级域名 + 放行子域名 / 精确URL：放行
        assertFalse(blocked(listOf(topBlack), listOf(subWhite)))
        assertFalse(blocked(listOf(topBlack), listOf(urlWhite)))
        // 拦子域名 + 放行该 URL：放行
        assertFalse(blocked(listOf(subBlack), listOf(urlWhite)))

        // 白名单规则不匹配该请求时，仍按黑名单拦截（不影响其它子域、其它 URL）
        val otherSubWhite = HostStr("https://other.example.com:443").apply { mode = BLOCK_MODE_SUBDOMAIN }
        val otherUrlWhite = HostStr("https://ads.example.com/other.js").apply { mode = BLOCK_MODE_URL }
        assertTrue(blocked(listOf(topBlack), listOf(otherSubWhite)))
        assertTrue(blocked(listOf(topBlack), listOf(otherUrlWhite)))
    }

    @Test
    fun `规则优先级 精确URL 大于 子域名 大于 顶级域名`() {
        val raw = "https://ads.example.com/ad.js"
        val u = url(raw)
        val topWhite = HostStr("example.com").apply { mode = BLOCK_MODE_DOMAIN }
        val topBlack = HostStr("example.com").apply { mode = BLOCK_MODE_DOMAIN }
        val subWhite = HostStr("https://ads.example.com:443").apply { mode = BLOCK_MODE_SUBDOMAIN }
        val subBlack = HostStr("https://ads.example.com:443").apply { mode = BLOCK_MODE_SUBDOMAIN }
        val urlWhite = HostStr(raw).apply { mode = BLOCK_MODE_URL }
        val urlBlack = HostStr(raw).apply { mode = BLOCK_MODE_URL }
        fun hit(black: List<HostStr>, white: List<HostStr>) =
            adBlockResolve(u, raw, "example.com", black, white)

        // 只有顶级域名规则时按其判定
        assertEquals("white", hit(emptyList(), listOf(topWhite))?.kind)
        assertEquals("black", hit(listOf(topBlack), emptyList())?.kind)

        // 子域名级压过顶级域名级（即使顶级域名是白名单）
        assertEquals("black", hit(listOf(subBlack), listOf(topWhite))?.kind)
        assertEquals("white", hit(listOf(topBlack), listOf(subWhite))?.kind)

        // 精确URL级压过子域名级与顶级域名级
        assertEquals("black", hit(listOf(urlBlack), listOf(subWhite, topWhite))?.kind)
        assertEquals("white", hit(listOf(subBlack, topBlack), listOf(urlWhite))?.kind)

        // 高优先级级别只有「不匹配本请求」的规则时，继续往下一级查
        val otherUrlBlack = HostStr("https://ads.example.com/other.js").apply { mode = BLOCK_MODE_URL }
        val otherSubBlack = HostStr("https://other.example.com:443").apply { mode = BLOCK_MODE_SUBDOMAIN }
        assertEquals("white", hit(listOf(otherUrlBlack), listOf(subWhite))?.kind)
        assertEquals("white", hit(listOf(otherSubBlack), listOf(topWhite))?.kind)

        assertNull(hit(emptyList(), emptyList()))
    }
}
