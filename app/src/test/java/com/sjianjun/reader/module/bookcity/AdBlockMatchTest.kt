package com.sjianjun.reader.module.bookcity

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertFalse
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
}
