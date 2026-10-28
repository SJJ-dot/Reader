package com.sjianjun.reader.module.bookcity

import org.junit.Assert.*
import org.junit.Test

/**
 * 针对“自维护前进/后退历史”的单元测试。
 * 直接驱动真实的 [TabHistory]，并用一个轻量 FakeTab 复刻 CustomWebView 的 loadUrl/onPageStarted 落栈逻辑，
 * 以模拟用户场景：A-B-C-B，后退到 B，退出再进，验证仍能后退/前进。
 *
 * 注：测试证明 WebView 原生 saveState/restoreState 不恢复历史栈（SDK 无注入 API），
 * 因此历史必须由我们自己的列表承载——这正是之前所有修复都失败的根因。
 */
/** 复刻应用里的 samePage：忽略 fragment 与尾斜杠、host 小写 */
private fun samePage(a: String?, b: String?): Boolean {
    if (a == null || b == null) return a == b
    fun norm(u: String) = u.lowercase().substringBefore('#').trimEnd('/')
    return norm(a) == norm(b)
}

class TabHistoryTest {

    /** 复刻 CustomWebView 里 loadUrl + onPageStarted 对历史的落栈/抑制逻辑（不真正加载 WebView） */
    private data class FakeTab(var url: String, var history: TabHistory, var suppressRecord: Boolean) {
        fun loadUrl(u: String) {
            url = u
            // 加载的正是历史当前页（重进/重显）→ 不重复压栈
            suppressRecord = history.currentUrl != null && samePage(u, history.currentUrl)
        }
        fun onPageStarted(u: String) {
            if (suppressRecord) suppressRecord = false else history.navigate(u)
            url = history.currentUrl ?: u
        }
    }

    private fun freshTab() = FakeTab("", TabHistory(), false)

    @Test
    fun `A-B-C-B 后退到B 退出再进后 能后退到C 并能前进回B`() {
        val tab = freshTab()
        // 真实导航 A -> B -> C -> B（每次 onPageStarted 落栈）
        listOf("A", "B", "C", "B").forEach {
            tab.loadUrl(it)
            tab.onPageStarted(it)
        }
        assertEquals(listOf("A", "B", "C", "B"), tab.history.entries)
        assertEquals(3, tab.history.currentIndex)
        assertTrue(tab.history.canGoBack)
        assertFalse(tab.history.canGoForward)

        // —— 模拟“退出再进”：历史以 JSON 落盘，再反序列化恢复（进程被杀也能恢复）——
        val persisted = tab.history.toJson()
        val restored = TabHistory.fromJson(persisted)!!
        val reTab = FakeTab(tab.url, restored, false)

        // 重进时重新 loadUrl 当前页 B（与历史当前页一致 → 抑制重复压栈）
        reTab.loadUrl(reTab.history.currentUrl!!)
        reTab.onPageStarted(reTab.history.currentUrl!!)
        assertEquals(listOf("A", "B", "C", "B"), reTab.history.entries) // 未被重复压栈
        assertEquals(3, reTab.history.currentIndex)
        assertTrue(reTab.history.canGoBack)    // 关键：重进后能后退
        assertFalse(reTab.history.canGoForward) // 已在栈顶，无可前进

        // 后退到 C
        val c = reTab.history.back()
        assertEquals("C", c)
        reTab.loadUrl(c!!)
        reTab.onPageStarted(c)
        assertTrue(reTab.history.canGoForward)  // 后退后可再前进
        assertEquals("C", reTab.history.currentUrl)

        // 前进回到 B
        val b = reTab.history.forward()
        assertEquals("B", b)
        assertFalse(reTab.history.canGoForward)
        assertEquals("B", reTab.history.currentUrl)

        // 一路后退到 A，到底
        assertEquals("C", reTab.history.back())
        assertEquals("B", reTab.history.back())
        assertEquals("A", reTab.history.back())
        assertNull(reTab.history.back())
        assertFalse(reTab.history.canGoBack)
    }

    @Test
    fun `真实导航会截断前进历史`() {
        val h = TabHistory()
        listOf("A", "B", "C").forEach { h.navigate(it) }
        h.back(); h.back() // 回到 A
        assertEquals("A", h.currentUrl)
        h.navigate("X") // 在 A 处跳到 X，应截断 B/C
        assertEquals(listOf("A", "X"), h.entries)
        assertFalse(h.canGoForward)
    }

    @Test
    fun `空历史与非法JSON返回null`() {
        assertNull(TabHistory.fromJson(null))
        assertNull(TabHistory.fromJson(""))
        assertNull(TabHistory.fromJson("not json"))
    }

    @Test
    fun `相同URL不重复落栈`() {
        val h = TabHistory()
        h.navigate("A")
        h.navigate("A")
        assertEquals(listOf("A"), h.entries)
        assertEquals(0, h.currentIndex)
    }
}
