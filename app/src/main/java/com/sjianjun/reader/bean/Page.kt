package com.sjianjun.reader.bean

/**
 * 表示一个网页的数据
 */
class Page {
    /**
     * 每个网页都必须有一个标题
     */
    @JvmField
    var title = ""

    /**
     * 网页中还会有其他网页
     */
    @JvmField
    var pageList: List<Page>? = null

    /**
     * 页面中会有书籍的分组列表
     */
    var bookGroupList: List<BookGroup>? = null


    class BookGroup {
        /**
         * 每个书籍分组都会有一个标题
         */
        var title = ""

        /**
         * 每个分组 都会有一个书籍列表
         */
        var bookList: List<Book>? = null

        /**
         * 获取下一页的数据 的Js 脚本方法
         */
        var next: String? = null
    }
}