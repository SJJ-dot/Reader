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
        if (mCollBook == null || chapterCategory == null) return

        // 将 BookChapter 转换成当前可用的 Chapter
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

        requestChapters(chapterPos - 1, chapterPos, chapterPos)
        return isRight
    }

    // 装载当前章内容。
    override fun parseCurChapter(): Boolean {
        val isRight = super.parseCurChapter()

        // 顺带预加载后面 NEXT_CHAPTER_PREFETCH 章
        requestChapters(chapterPos, chapterPos + NEXT_CHAPTER_PREFETCH, chapterPos)
        return isRight
    }

    // 装载下一章节的内容
    override fun parseNextChapter(): Boolean {
        val isRight = super.parseNextChapter()

        requestChapters(chapterPos, chapterPos + NEXT_CHAPTER_PREFETCH, chapterPos)

        return isRight
    }

    private fun requestChapters(start: Int, end: Int, first: Int) {
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

        // 过滤，哪些数据已经加载了；
        // 上一次加载失败的章节内容其实是错误提示（非空），这里要当成没加载，允许反复请求
        for (i in start..end) {
            val txtChapter = chapterCategory[i]
            if (!hasChapterData(txtChapter) || txtChapter.contentError) {
                chapters.add(txtChapter)
            }
        }

        if (!chapters.isEmpty()) {
            val firstChapter = chapterCategory[first]
            if (chapters.remove(firstChapter)) {
                chapters.add(0, firstChapter)
            }
            Log.i("requestChapters:${chapters.map { it.chapterIndex }}")
            listener.requestChapters(chapters)
        }
    }

    companion object {
        private const val TAG = "PageFactory"

        /** 预加载当前章之后的章节数（1 表示只预加载下一章） */
        private const val NEXT_CHAPTER_PREFETCH = 1
    }
}

