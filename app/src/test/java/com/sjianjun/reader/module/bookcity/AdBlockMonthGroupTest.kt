package com.sjianjun.reader.module.bookcity

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 验证黑白名单「按最近命中时间倒序 + 按月分组」的顶层纯函数。
 * 只构造 HostStr，不触碰 AdBlock 单例（其 LiveData 在纯 JVM 下会因缺少 Looper 崩溃）。
 */
class AdBlockMonthGroupTest {

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    private fun rule(host: String, time: String, hit: Long = 0) = HostStr(host).apply {
        this.time = time
        lastHitTime = hit
    }

    private fun ms(s: String): Long = fmt.parse(s)!!.time

    @Test
    fun `按最近命中时间倒序排序且月份倒序`() {
        val a = rule("a.com", "2026-01-01 00:00:00", ms("2026-03-05 10:00:00"))
        val b = rule("b.com", "2026-01-01 00:00:00", ms("2026-11-01 10:00:00"))
        val c = rule("c.com", "2026-01-01 00:00:00", ms("2026-09-20 10:00:00"))
        val groups = groupHostsByMonth(listOf(a, b, c))
        assertEquals(listOf("b.com", "c.com", "a.com"), groups.flatMap { it.entries }.map { it.host })
        assertEquals(listOf("2026-11", "2026-09", "2026-03"), groups.map { it.key })
        assertEquals(listOf("2026年11月", "2026年09月", "2026年03月"), groups.map { it.label })
    }

    @Test
    fun `从未命中的规则按创建时间归月`() {
        val hit = rule("hit.com", "2026-01-01 00:00:00", ms("2026-12-01 00:00:00"))
        val never = rule("never.com", "2026-06-15 12:00:00")
        val groups = groupHostsByMonth(listOf(never, hit))
        assertEquals(2, groups.size)
        assertEquals(listOf("hit.com", "never.com"), groups.flatMap { it.entries }.map { it.host })
        assertEquals("2026-12", groups[0].key)
        assertEquals("2026-06", groups[1].key)
    }

    @Test
    fun `同月规则内仍按命中时间倒序`() {
        val early = rule("early.com", "2026-01-01 00:00:00", ms("2026-11-02 08:00:00"))
        val late = rule("late.com", "2026-01-01 00:00:00", ms("2026-11-20 08:00:00"))
        val groups = groupHostsByMonth(listOf(early, late))
        assertEquals(1, groups.size)
        assertEquals(listOf("late.com", "early.com"), groups[0].entries.map { it.host })
    }

    @Test
    fun `创建时间缺失归入未知时间`() {
        val unknown = rule("unknown.com", "")
        val groups = groupHostsByMonth(listOf(unknown))
        assertEquals("unknown", groups.single().key)
        assertEquals("未知时间", groups.single().label)
        assertEquals(0L, hostSortTime(unknown))
    }
}
