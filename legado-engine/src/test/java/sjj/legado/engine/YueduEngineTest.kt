package sjj.legado.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class YueduEngineTest {

    @Test
    fun getSiteUrl_returnsBookSourceUrl() = runBlocking {
        val engine = YueduEngine("""
            {
              "bookSourceUrl": "https://example.com",
              "ruleSearch": {"checkKeyWord":"测试"}
            }
        """.trimIndent())
        assertEquals("https://example.com", engine.getSiteUrl())
    }

    @Test
    fun verify_returns2_whenSearchNotConfigured() = runBlocking {
        val engine = YueduEngine("""
            {
              "bookSourceUrl": "https://example.com"
            }
        """.trimIndent())
        assertEquals(2, engine.verify())
        assertEquals("[]", engine.search("关键词"))
    }
}

