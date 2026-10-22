package com.sjianjun.reader.module.bookcity

import android.app.ProgressDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.databinding.DialogWebBookCoverPickerBinding
import com.sjianjun.reader.databinding.ItemBookCoverSourceBinding
import com.sjianjun.reader.module.search.SearchViewModel
import com.sjianjun.reader.module.shelf.WebShelfViewModel
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import sjj.alog.Log
import kotlin.coroutines.resume

/** 浏览器操作（书架导入、阅读模式），从原设置面板抽取，无界面。 */
class BrowserActions(
    private val context: Context,
    private val customWebView: CustomWebView,
) {
    private var webBookImportJsCache: String? = null

    /** 阅读模式：先预提取正文写缓存，再打开阅读器 */
    fun openReadMode() {
        val wv = customWebView.webView ?: return
        val url = wv.url ?: return
        val scope = customWebView.lifecycleScope
        if (scope == null) {
            BrowserReadModeActivity.startActivity(context, url, customWebView.webBook?.id)
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
                BrowserReadModeActivity.startActivity(context, url, customWebView.webBook?.id)
            }
        }
    }
    fun addToShelf() {
        val wv = customWebView?.webView ?: return
        val url = wv.url ?: return

        val items = arrayOf(
            Pair("导入书源书架", R.mipmap.ic_shelf),
            Pair("导入网页书架", R.mipmap.ic_browser)
        )

        val adapter = object : ArrayAdapter<Pair<String, Int>>(
            context,
            R.layout.item_import_shelf_type,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_import_shelf_type, parent, false)
                val tvName = view.findViewById<TextView>(R.id.tv_name)
                val ivIcon = view.findViewById<ImageView>(R.id.iv_icon)
                val item = getItem(position)
                tvName.text = item?.first
                item?.second?.let { ivIcon.setImageResource(it) }
                return view
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("选择")
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> importToBookSourceShelf(url)
                    1 -> importToWebShelf(wv, url)
                }
            }
            .show()
    }

    /** 导入书源书架：通过 SearchViewModel.searchUrl 搜索并保存 */
    private fun importToBookSourceShelf(url: String) {
        val scope = customWebView?.lifecycleScope ?: return
        // 搜索通常要跑多个书源、耗时较长；用一个不可取消的进度框告诉用户"正在跑"，
        // 不至于让人以为按钮点了没反应。
        val progress = ProgressDialog(context).apply {
            setMessage("正在导入书源书架…")
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

    /** 导入网页书架：直接从 WebView 获取 HTML，解析封面后保存 */
    private fun importToWebShelf(wv: WebView, url: String) {
        val scope = customWebView?.lifecycleScope ?: return
        scope.launch {
            try {
                val fallbackTitle = wv.getTitleFromMeta().ifBlank { wv.title ?: "无标题" }.lines().joinToString("")
                val extract = wv.extractCoverCandidatesFromJs(fallbackTitle)
                val title = extract.title
                val webBook = customWebView?.webBook ?: WebBook().apply {
                    this.url = url
                    this.title = title.ifBlank { "未命名" }
                    this.lastUrl = url
                    this.lastTitle = title
                }
                val pickResult = pickCoverByDialog(title, extract.candidates) ?: return@launch
                webBook.title = pickResult.title
                webBook.cover = pickResult.cover
                webBook.url = url

                val vm = WebShelfViewModel()
                vm.insertWebBook(webBook)
                toast("已导入网页书架")
            } catch (e: Exception) {
                Log.e("导入网页书架失败", e)
                toast("导入失败: ${e.message}")
            }
        }
    }

    private suspend fun WebView.extractCoverCandidatesFromJs(fallbackTitle: String): CoverExtractResult {
        val js = loadWebBookImportJs(context)
        return suspendCancellableCoroutine { cont ->
            evaluateJavascript(js) { raw ->
                try {
                    val jsonStr = unwrapJsString(raw)
                    val obj = JSONObject(jsonStr)
                    val title = obj.optString("title", fallbackTitle).ifBlank { fallbackTitle }
                    val arr = obj.optJSONArray("coverCandidates")
                    val list = mutableListOf<CoverCandidate>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val url = item.optString("url")
                            if (url.isBlank()) continue
                            list.add(
                                CoverCandidate(
                                    url = url,
                                    source = item.optString("source", "页面图片"),
                                    score = item.optInt("score", 0),
                                )
                            )
                        }
                    }
                    if (cont.isActive) cont.resume(CoverExtractResult(title = title, candidates = list))
                } catch (t: Throwable) {
                    Log.e("extractCoverCandidatesFromJs err raw=$raw", t)
                    if (cont.isActive) cont.resume(CoverExtractResult(title = fallbackTitle, candidates = emptyList()))
                }
            }
        }
    }

    private fun loadWebBookImportJs(context: Context): String {
        webBookImportJsCache?.let { return it }
        val js = context.applicationContext.assets
            .open("web_book_extract.js")
            .bufferedReader()
            .use { it.readText() }
        webBookImportJsCache = js
        return js
    }

    private suspend fun pickCoverByDialog(title: String, candidates: List<CoverCandidate>): CoverPickResult? {
        val list = candidates.toMutableList()
        if (list.isEmpty()) {
            return CoverPickResult(title = title.ifBlank { "未命名" }, cover = null)
        }
        return suspendCancellableCoroutine { cont ->
            val dialogBinding = DialogWebBookCoverPickerBinding.inflate(LayoutInflater.from(context), null, false)
            dialogBinding.etTitleInput.setText(title)
            val adapter = WebCoverCandidateAdapter().apply { submitList(list) }
            dialogBinding.rvCoverSources.layoutManager = GridLayoutManager(context, 3)
            dialogBinding.rvCoverSources.adapter = adapter
            dialogBinding.swipeRefresh.isEnabled = false
            dialogBinding.btnClearCover.text = "不设置封面"
            dialogBinding.btnSave.text = "保存"

            val dialog = MaterialAlertDialogBuilder(context)
                .setView(dialogBinding.root)
                .setOnCancelListener {
                    if (cont.isActive) cont.resume(null)
                }
                .create()

            dialogBinding.btnClearCover.setOnClickListener {
                val inputTitle = dialogBinding.etTitleInput.text?.toString()?.trim().orEmpty().ifBlank { title.ifBlank { "未命名" } }
                if (cont.isActive) cont.resume(CoverPickResult(inputTitle, null))
                dialog.dismiss()
            }
            dialogBinding.btnSave.setOnClickListener {
                val inputTitle = dialogBinding.etTitleInput.text?.toString()?.trim().orEmpty()
                if (inputTitle.isBlank()) {
                    dialogBinding.titleInputLayout.error = "标题不能为空"
                    return@setOnClickListener
                }
                dialogBinding.titleInputLayout.error = null
                if (cont.isActive) {
                    val selectedUrl = adapter.getSelectedUrl()
                    if (selectedUrl == null) {
                        toast("请先选择封面")
                        return@setOnClickListener
                    }
                    cont.resume(CoverPickResult(inputTitle, selectedUrl))
                }
                dialog.dismiss()
            }

            dialogBinding.etTitleInput.requestFocus()
            dialog.show()
        }
    }

    private data class CoverPickResult(
        val title: String,
        val cover: String?,
    )

    private data class CoverExtractResult(
        val title: String,
        val candidates: List<CoverCandidate>,
    )

    private data class CoverCandidate(
        val url: String,
        val source: String,
        val score: Int,
    )

    private class WebCoverCandidateAdapter : RecyclerView.Adapter<WebCoverCandidateAdapter.CoverViewHolder>() {
        private val data = mutableListOf<CoverCandidate>()
        private var selectedPos = -1

        fun submitList(list: List<CoverCandidate>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        fun getSelectedUrl(): String? = data.getOrNull(selectedPos)?.url

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoverViewHolder {
            val binding = ItemBookCoverSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CoverViewHolder(binding)
        }

        override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
            val position = holder.getBindingAdapterPosition()
            val item = data[position]
            val selected = position == selectedPos
            holder.bind(item, selected)
            holder.binding.root.setOnClickListener {
                selectedPos = position
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int = data.size

        class CoverViewHolder(
            val binding: ItemBookCoverSourceBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: CoverCandidate, selected: Boolean) {
                binding.ivCover.glide(item.url)
                binding.tvSource.text = item.source
                if (selected) {
                    binding.root.setBackgroundResource(R.color.dn_color_primary)
                    binding.tvSource.setTextColor(R.color.mdr_textWhite_text.color(binding.root.context))
                } else {
                    binding.root.setBackgroundResource(R.color.dn_background_card)
                    binding.tvSource.setTextColor(R.color.dn_text_color_black.color(binding.root.context))
                }
            }
        }
    }

    private fun unwrapJsString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return "{}"
        return if (raw.startsWith("\"") && raw.endsWith("\"")) {
            JSONObject("{\"v\":$raw}").optString("v", "{}")
        } else {
            raw
        }
    }

}
