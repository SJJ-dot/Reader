package com.sjianjun.reader.module.main

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_DEFAULT_COMPARE
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.BOOK_READ_ONLY
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.CHAPTER_INDEX
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.databinding.MainFragmentBookSourceListBinding
import com.sjianjun.reader.databinding.MainItemBookSourceCompareBinding
import com.sjianjun.reader.databinding.MainItemFragmentBookSourceListBinding
import com.sjianjun.reader.module.search.SearchViewModel
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.fragmentCreate
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.id
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import com.sjianjun.reader.view.isLoading


@Suppress("UNCHECKED_CAST")
class BookSourceListFragment : BaseFragment() {
    private val bookTitle by lazy { requireArguments().getString(BOOK_TITLE) ?: "" }
    /** 是否默认进入「内容对比」模式（阅读页打开时为 true） */
    private val defaultCompare by lazy { requireArguments().getString(BOOK_DEFAULT_COMPARE) == "true" }
    var binding: MainFragmentBookSourceListBinding? = null
    private val vm by viewModels<BookSourceListViewModel>()
    private val searchVm by viewModels<SearchViewModel>()

    private val adapter by lazy { BookListAdapter(this) }
    private val compareAdapter by lazy { CompareAdapter(this) }

    private var compareMode = false
    private var compareBooks: List<Book> = emptyList()
    /** 对比页是否已完成首次填充（默认对比时只在首次数据到达时填充一次，避免闪烁） */
    private var compareInitialized = false
    /** 批量删除模式 */
    var deleteMode = false
    val selectedIds = mutableSetOf<String>()

    override fun getLayoutRes() = R.layout.main_fragment_book_source_list

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 与净化弹窗同款：无边框窗口，避免默认窗口背景/padding 干扰背景铺满
        setStyle(STYLE_NO_FRAME, R.style.dialog_NoActionBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.dialog_NoActionBar)
        // 窗口 edge-to-edge 配置必须在 show 之前完成（与 activity 在 setContentView 前
        // 调 enableEdgeToEdge 同理）。实测 show 后（onStart）再设置，decor 内容区不会
        // 重新铺满，导航栏区域会露出透明底（渲染为黑色）
        dialog.window?.apply {
            // 关闭对系统栏的 fit，窗口 frame 才能延伸覆盖导航栏区域
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                attributes.fitInsetsTypes = 0
            }
            // 顶部半透明压暗：dialog_NoActionBar 是普通主题，默认不带 dim，需显式开启。
            // 窗口白色背景已铺满含导航栏区域（不透明），dim 只叠加在上方露出的 activity 区域
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.dn_background)))
            setDecorFitsSystemWindows(false)
            WindowInsetsControllerCompat(this, decorView).isAppearanceLightNavigationBars = true
        }
        // 点击弹窗上方露出的空白区域（activity 区域）取消弹窗
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // 全宽、3/4 屏高、从底部弹起；窗口背景（onCreateDialog 已设）铺满窗口含导航栏区域
        dialog?.window?.apply {
            val height = (resources.displayMetrics.heightPixels * 3f / 4f).toInt()
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, height)
            setGravity(Gravity.BOTTOM)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MainFragmentBookSourceListBinding.bind(view)
        // 照净化弹窗：系统栏 + 刘海安全区转为根布局 padding，内容避开导航栏，背景仍然铺到底
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.displayCutout
            val safeLeft = maxOf(systemBars.left, cutout?.safeInsetLeft ?: 0)
            val safeRight = maxOf(systemBars.right, cutout?.safeInsetRight ?: 0)
            val safeBottom = maxOf(systemBars.bottom, cutout?.safeInsetBottom ?: 0)
            v.setPadding(safeLeft, 0, safeRight, safeBottom)
            insets
        }
        binding?.recycleView?.adapter = adapter
        binding?.viewPager?.adapter = compareAdapter
        binding?.viewPager?.offscreenPageLimit = 1
        // 内容对比翻页动画：轻微缩放 + 透明度
        binding?.viewPager?.setPageTransformer { page, position ->
            val scale = 0.85f + (1 - Math.abs(position)) * 0.15f
            page.scaleX = scale
            page.scaleY = scale
            page.alpha = 0.5f + (1 - Math.abs(position)) * 0.5f
        }
        // 滑动时跟随更新指示器
        binding?.viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                compareAdapter.updateIndicators()
            }
        })

        // 阅读页打开时默认进入内容对比
        compareMode = defaultCompare
        if (compareMode) refreshMode()

        vm.init(bookTitle)
        vm.bookList.observe(this) {
            compareBooks = it
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
            // 对比页不跟随 bookList 重绑：否则每次 DB 变化都会重新加载内容，页面反复闪烁。
            // 默认对比时仅首次数据到达后填充一次
            if (compareMode && !compareInitialized) {
                compareInitialized = true
                refreshMode()
            }
        }
        binding?.swipeRefresh?.setOnRefreshListener {
            launch {
                vm.reloadAllBookFromNet()
                binding?.swipeRefresh?.isRefreshing = false
            }
        }
        binding?.ivSearch?.click {
            // 搜索时自动切回列表模式，方便查看搜索结果
            if (compareMode) {
                compareMode = false
                refreshMode()
            }
            launch {
                binding?.swipeRefresh?.isRefreshing = true
                searchVm.search(bookTitle).collect { results ->
                    results.forEach { resultGroup ->
                        if (resultGroup.firstOrNull()?.bookTitle == bookTitle) {
                            searchVm.saveSearchResult(resultGroup)
                        }
                    }
                }
                binding?.swipeRefresh?.isRefreshing = false
                toast("搜索并保存完成")
            }
        }
        binding?.tvCompare?.click {
            compareMode = !compareMode
            refreshMode()
        }
        // 批量删除
        binding?.tvCancel?.click { exitDeleteMode() }
        binding?.tvSelectAll?.click {
            if (selectedIds.size == adapter.data.size) {
                selectedIds.clear()
            } else {
                selectedIds.clear()
                adapter.data.forEach { selectedIds.add(it.id) }
            }
            adapter.notifyDataSetChanged()
            updateDeleteBar()
        }
        binding?.tvDelete?.click {
            val toDelete = adapter.data.filter { selectedIds.contains(it.id) }
            if (toDelete.isEmpty()) {
                toast("请先选择要删除的书源")
                return@click
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("批量删除")
                .setMessage("确定删除选中的 ${toDelete.size} 个书源吗？")
                .setPositiveButton("删除") { _, _ ->
                    launch {
                        vm.deleteBooks(toDelete)
                        exitDeleteMode()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun refreshMode() {
        val b = binding ?: return
        b.swipeRefresh.visibility = if (compareMode) View.GONE else View.VISIBLE
        b.viewPager.visibility = if (compareMode) View.VISIBLE else View.GONE
        b.tvCompare.text = if (compareMode) "列表" else "内容对比"
        if (compareMode) {
            compareAdapter.data.clear()
            compareAdapter.data.addAll(compareBooks)
            compareAdapter.notifyDataSetChanged()
            // 默认定位到当前阅读书源那一页
            val index = compareBooks.indexOfFirst { it.id == it.record?.bookId }
            if (index >= 0) b.viewPager.setCurrentItem(index, false)
            compareAdapter.updateIndicators()
        }
    }

    // ==================== 批量删除 ====================

    private fun enterDeleteMode(bookId: String?) {
        deleteMode = true
        selectedIds.clear()
        bookId?.let { selectedIds.add(it) }
        adapter.notifyDataSetChanged()
        updateDeleteBar()
    }

    private fun exitDeleteMode() {
        deleteMode = false
        selectedIds.clear()
        adapter.notifyDataSetChanged()
        updateDeleteBar()
    }

    private fun toggleSelect(bookId: String) {
        if (selectedIds.contains(bookId)) selectedIds.remove(bookId) else selectedIds.add(bookId)
        adapter.notifyDataSetChanged()
        updateDeleteBar()
    }

    private fun updateDeleteBar() {
        val b = binding ?: return
        b.deleteBar.visibility = if (deleteMode) View.VISIBLE else View.GONE
        b.tvCancel.visibility = if (deleteMode) View.VISIBLE else View.GONE
        b.tvCompare.visibility = if (deleteMode) View.GONE else View.VISIBLE
        b.ivSearch.visibility = if (deleteMode) View.GONE else View.VISIBLE
        b.tvTitle.text = if (deleteMode) "已选 ${selectedIds.size} 项" else "书源列表"
        b.tvDelete.text = if (selectedIds.isEmpty()) "删除" else "删除(${selectedIds.size})"
        b.tvSelectAll.text = if (selectedIds.size == adapter.data.size) "取消全选" else "全选"
    }

    // ==================== 书源列表 ====================

    class BookListAdapter(val fragment: BookSourceListFragment) : BaseAdapter<Book>() {
        init {
            setHasStableIds(true)
        }

        override fun itemLayoutRes(viewType: Int) = R.layout.main_item_fragment_book_source_list

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val book = data[position]
            val binding = MainItemFragmentBookSourceListBinding.bind(holder.itemView)
            holder.itemView.apply {
                if (book.id == book.record?.bookId) {
                    binding.root.setCardBackgroundColor(R.color.dn_color_primary.color(fragment.context))
                } else {
                    binding.root.setCardBackgroundColor(R.color.dn_background_card.color(fragment.context))
                }
                val isNight = globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_YES
                val isCurrent = book.id == book.record?.bookId
                if (isCurrent && !isNight) {
                    binding.bookName.setTextColor(R.color.mdr_white.color(fragment.context))
                    binding.author.setTextColor(R.color.mdr_white.color(fragment.context))
                    binding.lastChapter.setTextColor(R.color.mdr_white.color(fragment.context))
                    binding.locateChapter.setTextColor(R.color.mdr_white.color(fragment.context))
                    binding.haveRead.setTextColor(R.color.mdr_white.color(fragment.context))
                } else {
                    binding.bookName.setTextColor(R.color.dn_text_color_black.color(fragment.context))
                    binding.author.setTextColor(R.color.dn_text_color_black.color(fragment.context))
                    binding.lastChapter.setTextColor(R.color.dn_text_color_black.color(fragment.context))
                    binding.haveRead.setTextColor(R.color.dn_text_color_black.color(fragment.context))
                    // 定位章节：当前正在阅读的书源保持 item 颜色不变；其余按是否同名显示绿/红
                    binding.locateChapter.setTextColor(
                        if (isCurrent) {
                            R.color.dn_text_color_black.color(fragment.context)
                        } else {
                            val same = book.readChapter?.title?.trim() == fragment.vm.currentChapterTitle?.trim()
                            (if (same) R.color.mdr_green_500 else R.color.mdr_red_600).color(fragment.context)
                        }
                    )
                }
                binding.bookCover.glide(book.cover)
                binding.bookName.text = book.title
                binding.author.text = "作者：${book.author}"
                binding.lastChapter.text = "最新：${book.lastChapter?.title}"
                binding.locateChapter.text = "定位：${book.readChapter?.title ?: "—"}"

                binding.haveRead.text = "${book.bookSource?.group}：${book.bookSource?.name}"
                binding.loading.isLoading = book.isLoading

                if (book.readChapter?.content?.firstOrNull()?.contentError == true) {
                    binding.bvUnread.setHighlight(false)
                } else {
                    binding.bvUnread.setHighlight(true)
                }
                if (book.isLoading) {
                    binding.bvUnread.hide()
                } else {
                    binding.bvUnread.show()
                }
                binding.bvUnread.badgeCount = book.unreadChapterCount

                // 批量删除模式：点击切换选中；否则点击切换书源
                click {
                    if (fragment.deleteMode) {
                        fragment.toggleSelect(book.id)
                    } else {
                        fragment.launch {
                            fragment.vm.changeReadingRecordBookSource(book)
                            fragment.dismissAllowingStateLoss()
                        }
                    }
                }
                // 长按进入批量删除模式
                setOnLongClickListener {
                    fragment.enterDeleteMode(book.id)
                    true
                }
                // 批量删除选中标记
                val selected = fragment.selectedIds.contains(book.id)
                binding.ivCheck.visibility = if (fragment.deleteMode) View.VISIBLE else View.GONE
                if (selected) {
                    binding.ivCheck.setBackgroundResource(R.drawable.bg_check_on)
                    binding.ivCheck.setImageResource(R.drawable.ic_check_white)
                } else {
                    binding.ivCheck.setBackgroundResource(R.drawable.bg_check_off)
                    binding.ivCheck.setImageDrawable(null)
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return data[position].id.id
        }
    }

    // ==================== 内容对比（左右滑动切换书源） ====================

    class CompareAdapter(val fragment: BookSourceListFragment) :
        RecyclerView.Adapter<CompareAdapter.VH>() {

        val data = mutableListOf<Book>()
        private val holders = mutableListOf<VH>()

        override fun getItemCount() = data.size

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.main_item_book_source_compare, parent, false)
            return VH(MainItemBookSourceCompareBinding.bind(view))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            if (!holders.contains(holder)) holders.add(holder)
            holder.bind(data[position])
        }

        override fun onViewRecycled(holder: VH) {
            holders.remove(holder)
            super.onViewRecycled(holder)
        }

        /** 翻页后刷新所有可见页的指示器 */
        fun updateIndicators() {
            holders.forEach { it.bindIndicator() }
        }

        inner class VH(val binding: MainItemBookSourceCompareBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(book: Book) {
                binding.tvSourceName.text = "${book.bookSource?.group}：${book.bookSource?.name}"
                // 作者
                binding.tvAuthor.text = book.author.takeIf { it.isNotBlank() }?.let { "作者：$it" } ?: ""
                binding.tvAuthor.visibility = if (book.author.isNotBlank()) View.VISIBLE else View.GONE
                // 当前正在阅读的书源显示「当前」标志
                binding.tvCurrent.visibility = if (book.id == book.record?.bookId) View.VISIBLE else View.GONE
                binding.btnSwitch.text = if (book.id == book.record?.bookId) "当前书源" else "切换书源"

                binding.btnSwitch.click {
                    if (book.id == book.record?.bookId) {
                        fragment.dismissAllowingStateLoss()
                        return@click
                    }
                    fragment.launch {
                        fragment.vm.changeReadingRecordBookSource(book)
                        fragment.dismissAllowingStateLoss()
                    }
                }
                binding.btnCatalog.click {
                    fragmentCreate<ChapterListFragment>(
                        BOOK_TITLE to book.title,
                        BOOK_ID to book.id,
                        BOOK_READ_ONLY to "true",
                        CHAPTER_INDEX to (book.readChapter?.index?.toString() ?: "")
                    ).show(fragment.childFragmentManager, "ChapterListFragment-${book.id}")
                }
                binding.btnRefresh.click { loadContent(book, force = true) }

                binding.tvLastChapter.text = book.lastChapter?.title?.let { "最新：$it" } ?: "最新：—"
                loadContent(book)
                bindIndicator()
            }

            /** 指示器：当前书源 / 总书源数 */
            fun bindIndicator() {
                val pos = fragment.binding?.viewPager?.currentItem ?: 0
                binding.tvIndicator.text = if (data.isNotEmpty()) "${pos + 1}/${data.size}" else ""
            }

            private fun loadContent(book: Book, force: Boolean = false) {
                binding.tvChapterTitle.text = "正在加载…"
                binding.tvContent.text = ""
                binding.btnRefresh.gone()
                binding.loading.show()
                fragment.launch {
                    val loaded = runCatching { fragment.vm.prepareForCompare(book, force) }.getOrNull()
                    //gone 而不是 hide：hide 是 INVISIBLE，进度条会继续占位撑出一片空白
                    binding.loading.gone()
                    val chapter = loaded?.readChapter
                    if (chapter == null) {
                        binding.tvChapterTitle.text = "未定位到章节"
                        binding.tvChapterTitle.setTextColor(R.color.mdr_red_600.color(fragment.context))
                        binding.tvLastChapter.text = loaded?.lastChapter?.title?.let { "最新：$it" } ?: "最新：—"
                        binding.tvContent.text = loaded?.error?.takeIf { it.isNotBlank() } ?: "暂无内容"
                        binding.btnRefresh.show()
                        return@launch
                    }
                    // 与阅读页/朗读一致的文本处理：HTML转纯文本 + 全角/简繁 + 净化规则
                    val (title, text) = runCatching { fragment.vm.formatCompareContent(loaded, chapter) }
                        .getOrElse {
                            (chapter.title ?: "") to
                                (chapter.content?.joinToString("\n") { it.content ?: "" } ?: "")
                        }
                    binding.tvChapterTitle.text = "当前：$title"
                    // 匹配到的章节名与当前阅读章节名完全相同时绿色，否则红色
                    val same = chapter.title?.trim() == fragment.vm.currentChapterTitle?.trim()
                    binding.tvChapterTitle.setTextColor(
                        (if (same) R.color.mdr_green_500 else R.color.mdr_red_600).color(fragment.context)
                    )
                    binding.tvLastChapter.text = loaded.lastChapter?.title?.let { "最新：$it" } ?: "最新：—"
                    binding.tvContent.text = text.ifBlank { "（该章暂无正文）" }
                
                    binding.btnRefresh.visibility = View.VISIBLE
                }
            }
        }
    }
}
