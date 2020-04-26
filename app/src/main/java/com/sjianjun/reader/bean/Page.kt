package com.sjianjun.reader.bean

import com.sjianjun.reader.utils.id

/**
 * 表示一个网页的数据
 */
class Page {
    /**
     * 当前页面的来源
     */
    @JvmField
    var source = ""
    /**
     * 每个网页都必须有一个标题
     */
    @JvmField
    var title = ""

    /**
     * 网页中还会有其他网页
     */
    @JvmField
    var pageList: List<Page> = emptyList()

    /**
     * 页面中会有书籍的分组列表
     */
    @JvmField
    var bookGroupList: List<BookGroup> = emptyList()


    /**
     * 加载当前页面的方法 不能为空
     */
    @JvmField
    var pageScript: String = ""

    /**
     * 页面引用计数。便于销毁
     */
    @JvmField
    var pageCount = 0

    val pageId: String by lazy { "$source $pageScript".id.toString() }

    class BookGroup {
        /**
         * 每个书籍分组都会有一个标题
         */
        @JvmField
        var title = ""

        /**
         * 每个分组 都会有一个书籍列表
         */
        @JvmField
        var bookList: List<Book>? = null

        /**
         * 获取下一页的数据 的Js 脚本方法
         */
        @JvmField
        var nextScript: String? = null

        override fun toString(): String {
            return "BookGroup(title='$title', bookList=$bookList, nextScript=$nextScript)"
        }

    }

    override fun toString(): String {
        return "Page(source='$source', title='$title', pageList=$pageList, bookGroupList=$bookGroupList, pageScript='$pageScript', pageCount=$pageCount)"
    }
}