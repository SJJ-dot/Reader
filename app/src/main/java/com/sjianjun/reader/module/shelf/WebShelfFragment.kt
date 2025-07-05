package com.sjianjun.reader.module.shelf

import android.annotation.SuppressLint
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.databinding.BookShelfTitleBinding
import com.sjianjun.reader.databinding.DialogWebBookInputBinding
import com.sjianjun.reader.databinding.FragmentWebShelfBinding
import com.sjianjun.reader.databinding.ItemWebBookListBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
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
            WebReaderActivity.startActivity(requireContext(), webBook.id)
        }
        adapter.onLongClickListener = { webBook ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除书籍")
                .setMessage("确定要删除书籍 ${webBook.title} 吗？")
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    launch {
                        try {
                            viewModel.deleteWebBook(webBook)
                            toast("书籍已删除")
                        } catch (e: Exception) {
                            Log.e("删除书籍失败", e)
                            toast("删除书籍失败: ${e.message ?: "未知错误"}")
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        binding?.fab?.click {
            val bindingDialog = DialogWebBookInputBinding.inflate(LayoutInflater.from(requireContext()))
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
                    if (adapter.data.contains(webBook)) {
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
                delay(100)
                adapter.notifyDataSetDiff(webBooks, { o, n -> o.id == n.id }, { o, n -> o == n })
            }
        }

    }

    class Adapter : BaseAdapter<WebBook>(R.layout.item_web_book_list) {
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        var onClickListener: ((WebBook) -> Unit)? = null
        var onLongClickListener: ((WebBook) -> Unit)? = null

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return data[position].id.id
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val webBook = data[position]
            val binding = ItemWebBookListBinding.bind(holder.itemView)
            binding.title.text = webBook.title
            binding.lastRead.text = if (webBook.lastTitle.isNotEmpty()) {
                "最近阅读: ${webBook.lastTitle}"
            } else {
                "最近阅读: 未阅读"
            }
            binding.lastUpdateTime.text = "更新时间: ${sdf.format(webBook.updateTime)}"
            binding.ivWebSite.glide(webBook.cover)
            binding.root.click {
                onClickListener?.invoke(webBook)
            }
            binding.root.setOnLongClickListener {
                onLongClickListener?.invoke(webBook)
                true
            }
        }
    }


}