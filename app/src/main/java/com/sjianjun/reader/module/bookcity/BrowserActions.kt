package com.sjianjun.reader.module.bookcity

import android.app.ProgressDialog
import android.content.Context
import com.sjianjun.reader.module.search.SearchViewModel
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sjj.alog.Log

/** 浏览器操作（书架导入、阅读模式），从原设置面板抽取，无界面。 */
class BrowserActions(
    private val context: Context,
    private val customWebView: CustomWebView,
) {

    /** 阅读模式：先预提取正文写缓存，再打开阅读器 */
    fun openReadMode() {
        val wv = customWebView.webView ?: return
        val url = wv.url ?: return
        // 记录打开阅读模式的来源浏览器标签页，便于切回浏览器时回到原页面
        val sourceTabId = customWebView.tabId
        Log.i("▶SwitchBrowser openReadMode sourceTabId=$sourceTabId")
        val scope = customWebView.lifecycleScope
        if (scope == null) {
            BrowserReadModeActivity.startActivity(context, url, sourceTabId = sourceTabId)
            return
        }
        scope.launch {
            try {
                val data = withTimeoutOrNull(2_000L) {
                    with(BrowserReadModeActivity.Companion) { wv.extractContent() }
                }
                if (data != null && data.content.isNotBlank()) {
                    BrowserReadModeActivity.putExtractResult(url, data)
                }
            } catch (t: Throwable) {
                Log.e("预提取阅读模式正文失败", t)
            } finally {
                BrowserReadModeActivity.startActivity(context, url, sourceTabId = sourceTabId)
            }
        }
    }

    /** 加入书架：直接导入书源书架，不再弹选择框 */
    fun addToShelf() {
        val url = customWebView.webView?.url ?: return
        importToBookSourceShelf(url)
    }

    /** 导入书源书架：通过 SearchViewModel.searchUrl 搜索并保存 */
    private fun importToBookSourceShelf(url: String) {
        val scope = customWebView.lifecycleScope ?: return
        // 搜索通常要跑多个书源、耗时较长；用一个不可取消的进度框告诉用户"正在跑"，
        // 不至于让人以为按钮点了没反应。
        val progress = ProgressDialog(context).apply {
            setMessage("正在导入书架…")
            setCancelable(false)
            show()
        }
        scope.launch {
            try {
                val vm = SearchViewModel()
                val results = vm.searchUrl(url)
                results.collect { groups ->
                    if (groups.isNotEmpty()) {
                        val allResults = groups.flatten()
                        vm.saveSearchResult(allResults)
                        toast("已导入书源书架")
                    } else {
                        toast("导入失败")
                    }
                }
            } catch (e: Exception) {
                Log.e("导入书源书架失败", e)
                toast("导入失败: ${e.message}")
            } finally {
                runCatching { progress.dismiss() }
            }
        }
    }
}
