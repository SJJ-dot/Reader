package sjj.legado.engine

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.isWebFile
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonObject
import sjj.alog.Log

/**
 * 阅读书源规则示例
 */
class YueduEngine(val rule: String) {
    private val source: BookSource = GSON.fromJsonObject<BookSource>(rule).getOrNull() ?: throw IllegalArgumentException("规则解析失败")
    init {
        SourceHelp.registerBookSource(source)
    }
    /**
     * 校验书源是否有效。0、1、2 分别代表：验证通过，验证失败，不支持验证
     * 如果书源不支持搜索则返回2
     */
    suspend fun verify(query: String): Int {
        val searchUrl = source.searchUrl
        if (searchUrl.isNullOrBlank()) {
            throw NoStackTraceException("书源不支持搜索")
        }
        val list = WebBook.searchBookAwait(source, query)
        if (list.isEmpty()) {
            throw NoStackTraceException("搜索失败，未获取到书籍")
        }
        if (list[0].toBook().isWebFile) {
            throw NoStackTraceException("不支持的文件类型书源")
        }
        val book = WebBook.getBookInfoAwait(source, Book(bookUrl = list[0].bookUrl.joinUrl(searchUrl)))
        val chapters = WebBook.getChapterListAwait(source, book).getOrThrow()
        val toc = chapters.filter { !(it.isVolume && it.url.startsWith(it.title)) }
        if (toc.isEmpty()) {
            throw NoStackTraceException("获取目录失败，未获取到正文章节")
        }
        val chapterUrl = toc[0].url.joinUrl(book.bookUrl.joinUrl(searchUrl))
        val content = WebBook.getContentAwait(source, Book(), BookChapter(chapterUrl))
        Log.i("≡已获取章节内容,${content}")
        if (content.length < 10) {
            throw NoStackTraceException("获取章节内容失败，内容过短")
        }
        return 0
    }

    /**
     * 获取书源网站地址或者主域名地址。会添加到书城列表。也用来判断书源是否支持某个链接。返回null代表不支持获取网站地址
     */
    suspend fun getSiteUrl(): String {
//        返回规则的 bookSourceUrl
        return source.bookSourceUrl
    }

    /**
    :param query: 搜索关键词
    :return: 书籍列表，包含以下字段：
    - bookTitle: 书籍标题
    - bookUrl: 书籍详情链接
    - bookAuthor: 书籍作者(可选)
    - bookCover: 书籍封面链接(可选)
     */
    suspend fun search(query: String): String? {
        val searchUrl = source.searchUrl
        if (searchUrl.isNullOrBlank()) {
            return GSON.toJson(emptyList<Map<String, String>>())
        }
        val list = WebBook.searchBookAwait(source, query)
        val books = list.mapNotNull {
            if (it.toBook().isWebFile) {
                null
            } else {
                mapOf(
                    "bookTitle" to it.name,
                    "bookUrl" to it.bookUrl.joinUrl(searchUrl),
                    "bookAuthor" to it.author,
                    "bookCover" to it.coverUrl?.joinUrl(it.bookUrl.joinUrl(searchUrl))
                )
            }
        }
        return GSON.toJson(books)
    }

    /**
     *     :return: json书籍详情字典，包含以下字段：
     *         - url: 书籍详情链接
     *         - title: 书籍标题
     *         - author: 书籍作者
     *         - cover: 书籍封面链接
     *         - intro: 书籍简介
     *         - chapterList: 章节列表，包含以下字段：
     *             - title: 章节标题
     *             - url: 章节链接
     */
    suspend fun getDetails(bookUrl: String): String? {
        val book = WebBook.getBookInfoAwait(source, Book(bookUrl = bookUrl))
        if (book.isWebFile) {
            val result = mapOf(
                "url" to book.bookUrl.joinUrl(bookUrl),
                "title" to book.name,
                "author" to book.author,
                "cover" to book.coverUrl?.joinUrl(book.bookUrl.joinUrl(bookUrl)),
                "intro" to book.intro,
                "chapterList" to emptyList<Any>()
            )
            return GSON.toJson(result)
        }
        val chapters = WebBook.getChapterListAwait(source, book).getOrThrow()
        val toc = chapters.filter { !(it.isVolume && it.url.startsWith(it.title)) }
        val result = mapOf(
            "url" to book.bookUrl.joinUrl(bookUrl),
            "title" to book.name,
            "author" to book.author,
            "cover" to book.coverUrl?.joinUrl(book.bookUrl.joinUrl(bookUrl)),
            "intro" to book.intro,
            "chapterList" to toc.map {
                mapOf(
                    "title" to it.title,
                    "url" to it.url.joinUrl(book.bookUrl.joinUrl(bookUrl))
                )
            }
        )
        return GSON.toJson(result)
    }

    /**
     * :param chapterUrl: 章节链接
     * :return: 章节内容字符串
     */
    suspend fun getChapterContent(chapterUrl: String): String {
        val content = WebBook.getContentAwait(source, Book(), BookChapter(chapterUrl))
        return content
    }
}

private fun String.joinUrl(baseUrl: String): String {
    if (this.startsWith("http://") || this.startsWith("https://")) {
        return this
    }
    return NetworkUtils.getAbsoluteURL(baseUrl, this)
}