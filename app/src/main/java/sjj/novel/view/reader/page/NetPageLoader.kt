package sjj.novel.view.reader.page

import sjj.alog.Log

/**
 * Created by newbiechen on 17-5-29.
 * 网络页面加载器
 *
 * 只负责加载**当前显示章节**：
 * - 上一章/当前章/下一章切换时，只请求当前章；
 * - 下一章的预加载由外部触发（阅读页在当前章加载成功后调用 `NovelChapterLoader`），
 *   预分页由外部调用 [preLoadNextChapter] 触发。
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

    // 装载上一章节的内容（chapterPos 已减 1，只请求这一章）
    override fun parsePrevChapter(): Boolean {
        val isRight = super.parsePrevChapter()

        requestCurrentChapter()
        return isRight
    }

    // 装载当前章内容
    override fun parseCurChapter(): Boolean {
        val isRight = super.parseCurChapter()

        requestCurrentChapter()
        return isRight
    }

    // 装载下一章节的内容（chapterPos 已加 1，只请求这一章）
    override fun parseNextChapter(): Boolean {
        val isRight = super.parseNextChapter()

        requestCurrentChapter()
        return isRight
    }

    /**
     * 只请求当前显示章节；已经有可用内容就不请求。
     *
     * 失败的章节（content 为空或 contentError=true）允许重复请求：
     * 翻页/切回来时会再次触发加载重试。
     */
    private fun requestCurrentChapter() {
        val listener = mPageChangeListener ?: return
        val chapterCategory = chapterCategory ?: return
        if (chapterCategory.isEmpty()) return

        val pos = chapterPos.coerceIn(0, chapterCategory.size - 1)
        val txtChapter = chapterCategory[pos]
        if (hasChapterData(txtChapter) && !txtChapter.contentError) return

        Log.i("requestChapters:${txtChapter.chapterIndex}")
        listener.requestChapters(mutableListOf(txtChapter))
    }
}
