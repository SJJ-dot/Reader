package sjj.novel.view.reader.page

import sjj.alog.Log
import kotlin.math.max
import kotlin.math.min


/**
 * Created by newbiechen on 17-5-29.
 * 网络页面加载器
 */
class NetPageLoader() : PageLoader() {
    public override fun refreshChapterList() {
        if (mCollBook == null || mCollBook!!.bookChapterList == null) return

        // 将 BookChapter 转换成当前可用的 Chapter
        chapterCategory = mCollBook!!.bookChapterList
        isChapterListPrepare = true

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
            // 如果上一章加载完成，加载上一章的内容
            requestChapters(chapterPos - 1, chapterPos - 1, chapterPos - 1)
        } else if (mStatus == STATUS_LOADING) {
            // 加载失败，加载当前章的内容
            requestChapters(chapterPos - 1, chapterPos, chapterPos)
        }
        return isRight
    }

    // 装载当前章内容。
    override fun parseCurChapter(): Boolean {
        val isRight = super.parseCurChapter()

        if (mStatus == STATUS_LOADING) {
            // 如果当前章加载失败，加载当前章的内容
            requestChapters(chapterPos, chapterPos + 2, chapterPos)
        }
        return isRight
    }

    // 装载下一章节的内容
    override fun parseNextChapter(): Boolean {
        val isRight = super.parseNextChapter()

        if (mStatus == STATUS_FINISH) {
            requestChapters(chapterPos + 1, chapterPos + 2, chapterPos + 1)
        } else if (mStatus == STATUS_LOADING) {
            requestChapters(chapterPos, chapterPos + 2, chapterPos)
        }

        return isRight
    }

    private fun requestChapters(start: Int, end: Int, first: Int) {
        Log.i("requestChapters: start = $start, end = $end")
        val listener = mPageChangeListener ?: return
        val chapterCategory = chapterCategory ?: return

        val start = max(0, start)
        val end = min(end, chapterCategory.size - 1)
        // 检验输入值
        if (start > end) {
            Log.w("requestChapters: start > end, start = $start, end = $end")
            return
        }

        val chapters = mutableListOf<TxtChapter>()

        // 过滤，哪些数据已经加载了
        for (i in start..end) {
            val txtChapter = chapterCategory[i]
            if (!hasChapterData(txtChapter)) {
                chapters.add(txtChapter)
            }
        }

        if (!chapters.isEmpty()) {
            val firstChapter = chapterCategory[first]
            if (chapters.remove(firstChapter)) {
                chapters.add(0, firstChapter)
            }
            listener.requestChapters(chapters)
        }
    }

    companion object {
        private const val TAG = "PageFactory"
    }
}

