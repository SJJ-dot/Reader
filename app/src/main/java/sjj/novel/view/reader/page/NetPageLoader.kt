package sjj.novel.view.reader.page

import sjj.alog.Log


/**
 * Created by newbiechen on 17-5-29.
 * 网络页面加载器
 */
class NetPageLoader(pageView: PageView) : PageLoader(pageView) {
    public override fun refreshChapterList() {
        if (mCollBook == null || mCollBook!!.bookChapterList == null) return

        // 将 BookChapter 转换成当前可用的 Chapter
        chapterCategory = mCollBook!!.bookChapterList
        isChapterListPrepare = true

        // 目录加载完成，执行回调操作。
        if (mPageChangeListener != null) {
            mPageChangeListener!!.onCategoryFinish(chapterCategory)
        }

        // 如果章节未打开
        if (!isChapterOpen) {
            // 打开章节
            openChapter()
        }
    }

    // 装载上一章节的内容
    override fun parsePrevChapter(): Boolean {
        val isRight = super.parsePrevChapter()

        if (mStatus == STATUS_FINISH) {
            loadPrevChapter()
        } else if (mStatus == STATUS_LOADING) {
            loadCurrentChapter()
        }
        return isRight
    }

    // 装载当前章内容。
    override fun parseCurChapter(): Boolean {
        val isRight = super.parseCurChapter()

        if (mStatus == STATUS_LOADING) {
            loadCurrentChapter()
        }
        return isRight
    }

    // 装载下一章节的内容
    override fun parseNextChapter(): Boolean {
        val isRight = super.parseNextChapter()

        if (mStatus == STATUS_FINISH) {
            loadNextChapter()
        } else if (mStatus == STATUS_LOADING) {
            loadCurrentChapter()
        }

        return isRight
    }

    /**
     * 加载当前页的前面两个章节
     */
    private fun loadPrevChapter() {
        if (mPageChangeListener != null) {
            val end = chapterPos
            var begin = end - 2
            if (begin < 0) {
                begin = 0
            }

            requestChapters(begin, end)
        }
    }

    /**
     * 加载前一页，当前页，后一页。
     */
    private fun loadCurrentChapter() {
        if (mPageChangeListener != null) {
            var begin = chapterPos
            var end = chapterPos
            val chapterCategory = chapterCategory ?: return
            // 是否当前不是最后一章
            if (end < chapterCategory.size) {
                end = end + 1
                if (end >= chapterCategory.size) {
                    end = chapterCategory.size - 1
                }
            }

            // 如果当前不是第一章
            if (begin != 0) {
                begin = begin - 1
                if (begin < 0) {
                    begin = 0
                }
            }

            requestChapters(begin, end)
        }
    }

    /**
     * 加载当前页的后两个章节
     */
    private fun loadNextChapter() {
        if (mPageChangeListener != null) {
            // 提示加载后两章

            val begin = chapterPos + 1
            var end = begin + 1
            val chapterCategory = chapterCategory ?: return
            // 判断是否大于最后一章
            if (begin >= chapterCategory.size) {
                // 如果下一章超出目录了，就没有必要加载了
                return
            }

            if (end > chapterCategory.size) {
                end = chapterCategory.size - 1
            }

            requestChapters(begin, end)
        }
    }

    private fun requestChapters(start: Int, end: Int) {
        var start = start
        var end = end
        Log.i("requestChapters: start = $start, end = $end", Exception())
        // 检验输入值
        if (start < 0) {
            start = 0
        }
        val chapterCategory = chapterCategory ?: return
        if (end >= chapterCategory.size) {
            end = chapterCategory.size - 1
        }


        val chapters: MutableList<TxtChapter> = ArrayList()

        // 过滤，哪些数据已经加载了
        for (i in start..end) {
            val txtChapter = chapterCategory.get(i)
            if (!hasChapterData(txtChapter)) {
                chapters.add(txtChapter)
            }
        }

        if (!chapters.isEmpty()) {
            mPageChangeListener!!.requestChapters(chapters)
        }
    }

    companion object {
        private const val TAG = "PageFactory"
    }
}

