package com.sjianjun.reader.module.shelf

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.databinding.DialogWebBookInputBinding
import com.sjianjun.reader.databinding.FragmentWebShelfBinding
import com.sjianjun.reader.databinding.ItemWebBookListBinding
import com.sjianjun.reader.module.bookcity.BrowserReadModeActivity
import com.sjianjun.reader.module.bookcity.WebViewBrowserActivity
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.utils.id
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import java.text.SimpleDateFormat
import java.util.Locale

class WebShelfFragment : BaseFragment() {

    private val viewModel: WebShelfViewModel by viewModels()
    private val bookShelfTitle = BookShelfTitle()
    private val adapter = Adapter()
    private var binding: FragmentWebShelfBinding? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_web_shelf, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentWebShelfBinding.bind(view)
        setHasOptionsMenu(true)
        init()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
        bookShelfTitle.destroyTileView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        bookShelfTitle.initTileView(this)
        bookShelfTitle.showSourceData(false)
    }

    private fun init() {
        binding?.recycleView?.adapter = adapter
        adapter.onClickListener = { webBook ->
            if (globalConfig.autoBrowserReadMode.value == true) {
                val url = webBook.lastUrl ?: webBook.url
                BrowserReadModeActivity.startActivity(requireContext(), url, webBook.id)
            } else {
                WebViewBrowserActivity.startActivity(requireContext(), id = webBook.id)
            }
        }
        adapter.onSwitchSourceClickListener = { grouped ->
            showSwitchSourceDialog(grouped)
        }
        adapter.onLongClickListener = { webBook ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(webBook.title)
                .setItems(arrayOf("修改标题", "修改封面", "删除")) { _, which ->
                    when (which) {
                        0 -> showEditTitleDialog(webBook.currentSelected)
                        1 -> WebViewBrowserActivity.startActivity(requireContext(), id = webBook.currentSelected.id)
                        2 -> showDeleteDialog(webBook)
                    }
                }
                .show()
        }
        binding?.fab?.click {
            val bindingDialog =
                DialogWebBookInputBinding.inflate(LayoutInflater.from(requireContext()))
            MaterialAlertDialogBuilder(requireContext())
//                .setTitle("导入书籍")
                .setView(bindingDialog.root)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    val title = bindingDialog.etTitleInput.text.toString().trim()
                    var url = bindingDialog.etUrlInput.text.toString().trim()
                    if (title.isEmpty()) {
                        toast("书名不能为空")
                        return@setPositiveButton
                    }
                    if (!url.startsWith("http")) {
                        url = "https://$url"
                    }
                    if (url.toHttpUrlOrNull() == null) {
                        toast("请输入正确的URL地址")
                        return@setPositiveButton
                    }
                    val webBook = WebBook().apply {
                        this.title = title
                        this.url = url
                    }
                    // 检查是否已存在相同url的书籍
                    val exists = adapter.data.any { grouped ->
                        grouped.webBooks.any { it.url == webBook.url }
                    }
                    if (exists) {
                        toast("书籍已存在")
                        return@setPositiveButton
                    }
                    launch {
                        try {
                            showSnackbar(binding!!.root, "正在导入...", Snackbar.LENGTH_INDEFINITE)
                            viewModel.importWebBook(webBook)
                            toast("导入成功")
                        } catch (e: Exception) {
                            Log.e("导入书籍失败", e)
                            toast("导入失败: ${e.message ?: "未知错误"}")
                        } finally {
                            dismissSnackbar()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        launch {
            viewModel.getWebBook().collectLatest { webBooks ->
//                Log.i("WebBooks updated: ${webBooks} items")
                // 按书名分组
                val groupedList = webBooks.groupBy { it.title }
                    .map { entry ->
                        val list = entry.value.toMutableList()
                        val selected = list.find { it.isSelected } ?: list.first()
                        GroupedWebBook(entry.key, list, selected)
                    }
//                Log.i(groupedList)
                adapter.notifyDataSetDiff(
                    groupedList,
                    { o, n -> o.title == n.title },
                    { o, n -> o == n })
            }
        }

    }

    private fun showEditTitleDialog(webBook: WebBook) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(webBook.title)
            setSelection(webBook.title.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("修改标题")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isEmpty()) {
                    toast("标题不能为空")
                    return@setPositiveButton
                }
                webBook.title = newTitle
                launch {
                    try {
                        viewModel.updateWebBook(webBook)
                        toast("标题已修改")
                    } catch (e: Exception) {
                        Log.e("修改标题失败", e)
                        toast("修改失败: ${e.message}")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(webBook: GroupedWebBook) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除书籍")
            .setMessage("确定要删除「${webBook.title}」吗？")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                launch {
                    try {
                        viewModel.deleteWebBook(webBook.currentSelected)
                        toast("已删除")
                    } catch (e: Exception) {
                        Log.e("删除书籍失败", e)
                        toast("删除失败: ${e.message}")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSwitchSourceDialog(grouped: GroupedWebBook) {
        val items = grouped.webBooks.map { webBook ->
            val domain = webBook.url.toHttpUrlOrNull()?.topPrivateDomain()
            if (webBook.isSelected) "✓ $domain" else domain
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("切换来源 - ${grouped.title}")
            .setItems(items) { _, which ->
                val selectedWebBook = grouped.webBooks[which]
                // 更新所有书籍选中状态
                grouped.webBooks.forEach { it.isSelected = (it == selectedWebBook) }
                grouped.currentSelected = selectedWebBook
                // 更新数据库
                launch {
                    grouped.webBooks.forEach { webBook ->
                        viewModel.updateWebBook(webBook)
                    }
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    data class GroupedWebBook(
        val title: String,
        val webBooks: MutableList<WebBook>,
        var currentSelected: WebBook
    )

    class Adapter : BaseAdapter<GroupedWebBook>(R.layout.item_web_book_list) {
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        var onClickListener: ((WebBook) -> Unit)? = null
        var onLongClickListener: ((GroupedWebBook) -> Unit)? = null
        var onSwitchSourceClickListener: ((GroupedWebBook) -> Unit)? = null

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return data[position].title.id
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val grouped = data[position]
            val currentSelected = grouped.currentSelected
            val binding = ItemWebBookListBinding.bind(holder.itemView)
            binding.title.text = currentSelected.title
            binding.lastRead.text = if (currentSelected.lastTitle.isNotEmpty()) {
                "最近阅读: ${currentSelected.lastTitle}"
            } else {
                "最近阅读: 未阅读"
            }
            binding.lastUpdateTime.text = "更新时间: ${sdf.format(currentSelected.updateTime)}"
            binding.ivWebSite.glide(currentSelected.cover)

            // 显示来源信息
            val sourceCount = grouped.webBooks.size
            val currentDomain = currentSelected.url.toHttpUrlOrNull()?.topPrivateDomain()
            binding.tvSourceInfo.text = "来源: $sourceCount ($currentDomain)"

            // 只有当来源数量大于1时才显示切换按钮
            if (sourceCount > 1) {
                binding.ivSwitchSource.visibility = View.VISIBLE
                binding.ivSwitchSource.click {
                    onSwitchSourceClickListener?.invoke(grouped)
                }
            } else {
                binding.ivSwitchSource.visibility = View.GONE
            }

            binding.root.click {
                onClickListener?.invoke(currentSelected)
            }
            binding.root.setOnLongClickListener {
                onLongClickListener?.invoke(grouped)
                true
            }
        }
    }

}
