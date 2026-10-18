package com.sjianjun.reader.module.main


import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.viewModels
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.BOOK_READ_ONLY
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.CHAPTER_INDEX
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.databinding.ItemTextTextBinding
import com.sjianjun.reader.databinding.MainFragmentBookChapterListBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.setTextColorRes
import com.sjianjun.reader.utils.startActivity
import com.sjianjun.reader.view.click


/**
 *展示章节列表
 */
class ChapterListFragment : BaseFragment() {
    val bookTitle: String get() = requireArguments().getString(BOOK_TITLE)!!
    val bookId: String? get() = requireArguments().getString(BOOK_ID)
    private val readOnly: Boolean get() = requireArguments().getString(BOOK_READ_ONLY)?.toBoolean() ?: false
    /** 换源内容对比里定位到的章节序号；非空时优先用它高亮/滚动，而不是阅读记录里的序号 */
    private val targetChapterIndex: Int? get() = requireArguments().getString(CHAPTER_INDEX)?.toIntOrNull()
    private val adapter = ChapterListAdapter(this)
    private var binding: MainFragmentBookChapterListBinding? = null
    private val viewModel by viewModels<ChapterListViewModel>()

    override fun getLayoutRes() = R.layout.main_fragment_book_chapter_list

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.dialog_NoActionBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.dialog_NoActionBar)
        // 与换源弹窗一致的窗口配置：edge-to-edge 铺满导航栏区域，底部对齐、高度一致
        dialog.window?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                attributes.fitInsetsTypes = 0
            }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.dn_background)))
            setDecorFitsSystemWindows(false)
            WindowInsetsControllerCompat(this, decorView).isAppearanceLightNavigationBars = true
        }
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment_book_chapter_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = MainFragmentBookChapterListBinding.bind(view)
        // 窗口已 edge-to-edge 延伸到导航栏下方，底部按导航栏高度加 padding，避免内容被遮挡
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
        binding?.recycleViewChapterList?.adapter = adapter
        initRefresh()
        initData()
    }

    override fun onStart() {
        super.onStart()
        // 作为「目录」弹窗（换源内容对比）时：右侧滑出，宽度约 82%、高度与换源弹窗一致
        dialog?.window?.apply {
            val metrics = resources.displayMetrics
            setLayout((metrics.widthPixels * 0.82f).toInt(), (metrics.heightPixels * 3f / 4f).toInt())
            setGravity(Gravity.END or Gravity.BOTTOM)
            setWindowAnimations(R.style.right_slide_anim)
        }
    }

    /**
     * 章节列表：下拉「重新加载」（全量更新章节列表），上拉「检查更新」（增量拉取最新章节）
     */
    private fun initRefresh() {
        val refresh = binding?.chapterRefresh ?: return
        //记录动画是哪个手势拉起来的，收尾时按对应方式收
        var fromLoadMore = false
        //同一个 key：重复下拉/上拉时新的加载会取代上一次（协程取消会连带取消正在跑的脚本调用）
        refresh.setOnRefreshListener {
            fromLoadMore = false
            launch(singleCoroutineKey = "chapter_list_load") { viewModel.reloadBookFromNet(bookTitle, bookId) }
        }
        refresh.setEnableLoadMore(true)
        //默认是「拉到底自动加载」，会一上拉就触发，这里改为松手才执行
        refresh.setEnableAutoLoadMore(false)
        refresh.setOnLoadMoreListener {
            fromLoadMore = true
            launch(singleCoroutineKey = "chapter_list_load") { viewModel.checkUpdate(bookTitle, bookId) }
        }
        //刷新动画跟随书籍的加载状态，而不是等请求返回后再手动收：
        //请求可能长时间不返回（脚本卡住、网络慢），手动收会让控件一直停在「正在刷新／加载中」，
        //此后下拉、上拉全部失效；页面退出时加载状态会被重置，动画也随之收掉
        viewModel.bookIsLoadingLiveData.observeViewLifecycle { loading ->
            if (loading == true) {
                if (!refresh.isRefreshing && !refresh.isLoading) {
                    if (fromLoadMore) refresh.autoLoadMoreAnimationOnly()
                    else refresh.autoRefreshAnimationOnly()
                }
            } else {
                if (refresh.isRefreshing) refresh.finishRefresh()
                if (refresh.isLoading) refresh.finishLoadMore()
            }
        }
    }

    private fun initData() {
        viewModel.init(bookTitle, bookId)
        viewModel.chapterListLiveData.observeViewLifecycle {
            var change = adapter.readingChapterIndex != adapter.readingRecord?.chapterIndex
            change = change || (adapter.data.size != it.size)
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
            if (change){
                val index = adapter.data.indexOfFirst {
                    it.index == adapter.readingChapterIndex
                }
                if (index > 0)
                    binding?.recycleViewChapterList?.scrollToPosition(index)
            }
        }
        viewModel.readingRecord.observeViewLifecycle {
            adapter.readingRecord = it
            // 换源内容对比里的「目录」：优先用定位到的章节序号高亮/滚动
            adapter.readingChapterIndex = targetChapterIndex ?: (it?.chapterIndex ?: 0)
            val index = adapter.data.indexOfFirst {
                it.index == adapter.readingChapterIndex
            }
            if (index > 0)
                binding?.recycleViewChapterList?.scrollToPosition(index)
        }
        // 净化规则变更后，重新加载章节列表以刷新标题
        EventBus.observe(EventKey.REPLACEMENT_RULES_CHANGED, viewLifecycleOwner, androidx.lifecycle.Observer<String> {
            viewModel.init(bookTitle, bookId)
        })
    }

    private class ChapterListAdapter(val fragment: ChapterListFragment) : BaseAdapter<Chapter>() {
        init {
            setHasStableIds(true)
        }

        var readingChapterIndex = 0
        var readingRecord: ReadingRecord? = null


        override fun itemLayoutRes(viewType: Int): Int {
            return R.layout.item_text_text
        }

        override fun onBindViewHolder(
            holder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
            position: Int
        ) {
            val binding = ItemTextTextBinding.bind(holder.itemView)
            holder.itemView.apply {
                val c = data[position]
                binding.text1.text = c.title
                if (readingChapterIndex == c.index) {
                    binding.text1.setTextColorRes(R.color.mdr_green_500)
                } else {
                    binding.text1.setTextColorRes(R.color.dn_text_color_light)
                }
                if (c.isLoaded) {
                    binding.mark.setBackgroundColor(R.color.mdr_green_A700.color(context))
                } else {
                    binding.mark.setBackgroundColor(R.color.mdr_grey_500.color(context))
                }
                click {
                    // 换源内容对比里的「目录」仅作查看，不跳转、不改阅读记录
                    if (fragment.readOnly) return@click
                    if (readingChapterIndex == c.index && fragment.activity is BookReaderActivity) {
                        //如果是当前章节，直接返回
                        return@click
                    }
                    fragment.launch {
                        //如果不是当前章节，更新阅读记录
                        fragment.viewModel.saveRecord(c)
                        fragment.startActivity<BookReaderActivity>(BOOK_ID to c.bookId)

                    }
                }
            }

        }

        override fun getItemId(position: Int): Long {
            return data[position].index.toLong()
        }
    }

}
