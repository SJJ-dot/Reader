package io.legado.app.data.entities

import android.os.Parcelable
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.time.LocalDate

@Parcelize
data class Book(
    // 详情页Url(本地书源存储完整文件路径)
    override var bookUrl: String = "",
    var tocUrl: String = "",
    // 书源URL(默认BookType.local)
    var origin: String = BookType.localTag,
    //书源名称 or 本地书籍文件名
    var originName: String = "",
    // 书籍名称(书源获取)
    override var name: String = "",
    // 作者名称(书源获取)
    override var author: String = "",
    // 分类信息(书源获取)
    override var kind: String? = null,
    // 分类信息(用户修改)
    var customTag: String? = null,
    // 封面Url(书源获取)
    var coverUrl: String? = null,
    // 封面Url(用户修改)
    var customCoverUrl: String? = null,
    // 简介内容(书源获取)
    var intro: String? = null,
    // 简介内容(用户修改)
    var customIntro: String? = null,
    // 自定义字符集名称(仅适用于本地书籍)
    var charset: String? = null,
    // 类型,详见BookType
    var type: Int = BookType.text,
    // 自定义分组索引号
    var group: Long = 0,
    // 最新章节标题
    var latestChapterTitle: String? = null,
    // 最新章节标题更新时间
    var latestChapterTime: Long = System.currentTimeMillis(),
    // 最近一次更新书籍信息的时间
    var lastCheckTime: Long = System.currentTimeMillis(),
    // 最近一次发现新章节的数量
    var lastCheckCount: Int = 0,
    // 书籍目录总数
    var totalChapterNum: Int = 0,
    // 当前章节名称
    var durChapterTitle: String? = null,
    // 当前章节索引
    var durChapterIndex: Int = 0,
    // 当前阅读的进度(首行字符的索引位置)
    var durChapterPos: Int = 0,
    // 最近一次阅读书籍的时间(打开正文的时间)
    var durChapterTime: Long = System.currentTimeMillis(),
    //字数
    override var wordCount: String? = null,
    // 刷新书架时更新书籍信息
    var canUpdate: Boolean = true,
    // 手动排序
    var order: Int = 0,
    //书源排序
    var originOrder: Int = 0,
    // 自定义书籍变量信息(用于书源规则检索书籍信息)
    override var variable: String? = null,
    //阅读设置
    var readConfig: ReadConfig? = null,
    //同步时间
    var syncTime: Long = 0L
) : Parcelable, BaseBook {

    override fun equals(other: Any?): Boolean {
        if (other is Book) {
            return other.bookUrl == bookUrl
        }
        return false
    }

    override fun hashCode(): Int {
        return bookUrl.hashCode()
    }

    @delegate:Transient
    @IgnoredOnParcel
    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    @IgnoredOnParcel
    override var infoHtml: String? = null

    @IgnoredOnParcel
    override var tocHtml: String? = null

    @IgnoredOnParcel
    var downloadUrls: List<String>? = null

    fun getRealAuthor() = author.replace(AppPattern.authorRegex, "")

    //自定义简介有自动更新的需求时，可通过更新intro再调用upCustomIntro()完成
    @Suppress("unused")
    fun upCustomIntro() {
        customIntro = intro
    }

    @IgnoredOnParcel
    val config: ReadConfig
        get() {
            if (readConfig == null) {
                readConfig = ReadConfig()
            }
            return readConfig!!
        }

    fun getReverseToc(): Boolean {
        return config.reverseToc
    }


    fun getUseReplaceRule(): Boolean {
        return true
    }

    fun toSearchBook() = SearchBook(
        name = name,
        author = author,
        kind = kind,
        bookUrl = bookUrl,
        origin = origin,
        originName = originName,
        type = type,
        wordCount = wordCount,
        latestChapterTitle = latestChapterTitle,
        coverUrl = coverUrl,
        intro = intro,
        tocUrl = tocUrl,
        originOrder = originOrder,
        variable = variable
    ).apply {
        this.infoHtml = this@Book.infoHtml
        this.tocHtml = this@Book.tocHtml
    }


    @Parcelize
    data class ReadConfig(
        var reverseToc: Boolean = false,
        var pageAnim: Int? = null,
        var reSegment: Boolean = false,
        var imageStyle: String? = null,
        var useReplaceRule: Boolean? = null,// 正文使用净化替换规则
        var delTag: Long = 0L,//去除标签
        var ttsEngine: String? = null,
        var splitLongChapter: Boolean = true,
        var readSimulating: Boolean = false,
        var startDate: LocalDate? = null,
        var startChapter: Int? = null,     // 用户设置的起始章节
        var dailyChapters: Int = 3    // 用户设置的每日更新章节数
    ) : Parcelable

}