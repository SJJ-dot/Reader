package com.sjianjun.reader.module.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.coorchice.library.SuperTextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.BOOK_TITLE
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.FontInfo
import com.sjianjun.reader.databinding.ItemFontBinding
import com.sjianjun.reader.databinding.ItemReaderSettingPopupActionBinding
import com.sjianjun.reader.databinding.ItemReaderSettingPopupDetailBinding
import com.sjianjun.reader.databinding.ItemReaderSettingPopupSwitchBinding
import com.sjianjun.reader.databinding.PopupReaderSettingMenuBinding
import com.sjianjun.reader.databinding.ReaderFragmentSettingViewBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.module.main.BookSourceListFragment
import com.sjianjun.reader.module.reader.activity.BookReaderViewModel
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.TtsUtil
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.colorText
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.format
import com.sjianjun.reader.utils.fragmentCreate
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.utils.htmlToSpanned
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sjj.alog.Log
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.NetPageLoader
import sjj.novel.view.reader.page.PageLoader
import sjj.novel.view.reader.page.PageStyle
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/*
 * Created by shen jian jun on 2020-07-13
 */
class BookReaderSettingFragment : BaseFragment() {
    var binding: ReaderFragmentSettingViewBinding? = null
    private var settingPopupWindow: PopupWindow? = null

    // 启动系统文件浏览器的请求码
    val adapter = FontAdapter()
    private val REQUEST_CODE_PICK_FONT = 1111
    private val pageLoader: PageLoader by activityViewModels<NetPageLoader>()
    private val readerViewModel: BookReaderViewModel by activityViewModels<BookReaderViewModel>()
    private val ttsUtil: TtsUtil? by activityViewModels<TtsUtil>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.reader_fragment_setting_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = ReaderFragmentSettingViewBinding.bind(view)
        val bottomSetting = binding?.settingBottomContainer!!
        val root = binding?.root!!
        val topBar = binding?.topBar!!
        ViewCompat.setOnApplyWindowInsetsListener(view) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.displayCutout
            val safeLeft = maxOf(systemBars.left, cutout?.safeInsetLeft ?: 0)
            val safeTop = maxOf(systemBars.top, cutout?.safeInsetTop ?: 0)
            val safeRight = maxOf(systemBars.right, cutout?.safeInsetRight ?: 0)
            val safeBottom = maxOf(systemBars.bottom, cutout?.safeInsetBottom ?: 0)
            bottomSetting.setPadding(bottomSetting.paddingLeft, bottomSetting.paddingTop, bottomSetting.paddingRight, maxOf(safeBottom, bottomSetting.paddingBottom))
            topBar.setPadding(topBar.paddingLeft, maxOf(topBar.paddingTop, safeTop), topBar.paddingRight, topBar.paddingBottom)
            root.setPadding(safeLeft, 0, safeRight, 0)
            insets
        }
        initSpeak()
        initChapterList()
        initChapterError()
        initChapterSync()
        initDayNight()
        initFontSize()
        initLineSpacing()
        initPageStyle()
        initPageModel()
        initFontList()
        initBrowser()
        initSettings()
        initScreenOrientationMode()
        initBrightnessSetting()
        initTopBar()
        initCacheChapter()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        binding?.settingContainerFirst?.isVisible = true
        binding?.settingContainerSecond?.isVisible = false
        refreshChapterProgress()
        updateBrightnessUi(globalConfig.readerBrightnessPercent.value ?: -1)
        updateBookDetailExpanded(false)
        refreshBookInfo()
    }

    private fun showPopupMenu(anchor: View) {
        if (settingPopupWindow?.isShowing == true) {
            settingPopupWindow?.dismiss()
            return
        }
        val popupAdapter = ReaderSettingPopupAdapter()
        val popupBinding = PopupReaderSettingMenuBinding.inflate(layoutInflater)
        popupBinding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        popupBinding.recyclerView.adapter = popupAdapter
        val popupItems = buildPopupItems(popupAdapter)
        popupAdapter.submitList(popupItems)


        val contentHeight = popupItems.size * 40.dp2Px + 12.dp2Px
        val popupWidth = maxOf(220.dp2Px, anchor.width)
        val popupHeight = minOf(contentHeight, (resources.displayMetrics.heightPixels * 0.5f).toInt())
        popupBinding.root.layoutParams = RecyclerView.LayoutParams(
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        popupBinding.recyclerView.layoutParams = popupBinding.recyclerView.layoutParams.apply {
            width = popupWidth
            height = popupHeight
        }
        popupBinding.recyclerView.minimumHeight = popupHeight
        popupBinding.recyclerView.requestLayout()

        val popupWindow = PopupWindow(
            popupBinding.root,
            popupWidth,
            popupHeight,
            true,
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            elevation = 8.dp2Px.toFloat()
        }
        settingPopupWindow = popupWindow
        val xOff = anchor.width - popupWidth
        popupWindow.showAsDropDown(anchor, xOff, 8.dp2Px)
    }

    private fun buildPopupItems(adapter: ReaderSettingPopupAdapter): List<ReaderSettingPopupItem> {
        val jianFanMode = (globalConfig.readerJianFanMode.value ?: PageLoader.MODE_JIAN_FAN_OFF)
            .coerceIn(PageLoader.MODE_JIAN_FAN_OFF, PageLoader.MODE_FAN_TO_JIAN)
        val typesettingMode = (globalConfig.readerTypesettingMode.value ?: PageLoader.MODE_TYPESETTING_HORIZONTAL_LTR)
            .coerceIn(PageLoader.MODE_TYPESETTING_HORIZONTAL_LTR, PageLoader.MODE_TYPESETTING_VERTICAL_RTL)
        return listOf(
            ReaderSettingPopupItem.Detail(title = "简繁转换", value = arrayOf("关闭", "简体转繁体", "繁体转简体")[jianFanMode]) { item ->
                val options = arrayOf("关闭", "简体转繁体", "繁体转简体")
                val current = (globalConfig.readerJianFanMode.value ?: PageLoader.MODE_JIAN_FAN_OFF)
                    .coerceIn(PageLoader.MODE_JIAN_FAN_OFF, PageLoader.MODE_FAN_TO_JIAN)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("简繁转换")
                    .setSingleChoiceItems(options, current) { dialog, which ->
                        globalConfig.readerJianFanMode.postValue(which)
                        dialog.dismiss()
                        item.value = options[which]
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
            ReaderSettingPopupItem.Detail(title = "文字排版", value = arrayOf("横排左起", "横排右起", "竖排左起", "竖排右起")[typesettingMode]) { item ->
                val options = arrayOf("横排左起", "横排右起", "竖排左起", "竖排右起")
                val current = (globalConfig.readerTypesettingMode.value ?: PageLoader.MODE_TYPESETTING_HORIZONTAL_LTR)
                    .coerceIn(PageLoader.MODE_TYPESETTING_HORIZONTAL_LTR, PageLoader.MODE_TYPESETTING_VERTICAL_RTL)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("文字排版")
                    .setSingleChoiceItems(options, current) { dialog, which ->
                        globalConfig.readerTypesettingMode.postValue(which)
                        dialog.dismiss()
                        item.value = options[which]
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
            ReaderSettingPopupItem.Action(title = "点击区域") {
                hide()
                if (parentFragmentManager.findFragmentByTag(ReaderClickAreaSettingDialogFragment.TAG) == null) {
                    ReaderClickAreaSettingDialogFragment().show(parentFragmentManager, ReaderClickAreaSettingDialogFragment.TAG)
                }
            },
            ReaderSettingPopupItem.Action(title = "净化替换") {
                hide()
                if (parentFragmentManager.findFragmentByTag(ReplacementRuleListDialogFragment.TAG) == null) {
                    ReplacementRuleListDialogFragment().show(parentFragmentManager, ReplacementRuleListDialogFragment.TAG)
                }
            },
            ReaderSettingPopupItem.Switch(title = "音量键翻页", checked = globalConfig.readerVolumeKeyPageTurn.value == true) { item, checked ->
                item.checked = checked
                globalConfig.readerVolumeKeyPageTurn.postValue(checked)
            },
        )
    }

    private fun initTopBar() {
        binding?.topBarBack?.click(10) {
            activity?.finish()
        }
        binding?.topBarSettingsMore?.click(100) {
            showPopupMenu(it)
        }
        readerViewModel.book.observe(viewLifecycleOwner) {
            refreshBookInfo()
        }
        binding?.bookTitle?.click {
            updateBookDetailExpanded(binding?.bookDetail1?.isVisible != true)
        }
        binding?.bookDetail1?.click {
            updateBookDetailExpanded(false)
        }
        binding?.bookIntro?.click {
            updateBookDetailExpanded(false)
        }
        binding?.bookIntro?.setOnLongClickListener {
            val book = readerViewModel.book.value ?: return@setOnLongClickListener true
            if (parentFragmentManager.findFragmentByTag(BookIntroPickerDialogFragment.TAG) == null) {
                BookIntroPickerDialogFragment.newInstance(book.title, book.id)
                    .show(parentFragmentManager, BookIntroPickerDialogFragment.TAG)
            }
            true
        }
        binding?.originClickableArea?.click {
            val book = readerViewModel.book.value ?: return@click
            fragmentCreate<BookSourceListFragment>(
                BOOK_TITLE to book.title
            ).show(parentFragmentManager, "BookSourceListFragment")
            hide()
        }
        readerViewModel.contentError.observe(viewLifecycleOwner) {
            refreshBookInfo()
        }
        EventBus.observe(EventKey.BOOK_COVER_CHANGED, viewLifecycleOwner, Observer<String> {
            if (it == readerViewModel.book.value?.id) {
                lifecycleScope.launch {
                    val book = readerViewModel.book.value ?: return@launch
                    book.record = withIo {
                        DbFactory.db.readingRecordDao().getReadingRecordSync(book.title)
                    }
                    refreshBookInfo()
                }
            }
        })
        EventBus.observe(EventKey.BOOK_INTRO_CHANGED, viewLifecycleOwner, Observer<String> {
            if (it == readerViewModel.book.value?.id) {
                lifecycleScope.launch {
                    val book = readerViewModel.book.value ?: return@launch
                    book.record = withIo {
                        DbFactory.db.readingRecordDao().getReadingRecordSync(book.title)
                    }
                    refreshBookInfo()
                }
            }
        })
        binding?.bookCover?.setOnLongClickListener {
            val book = readerViewModel.book.value ?: return@setOnLongClickListener true
            if (parentFragmentManager.findFragmentByTag(BookCoverPickerDialogFragment.TAG) == null) {
                BookCoverPickerDialogFragment.newInstance(book.title, book.id)
                    .show(parentFragmentManager, BookCoverPickerDialogFragment.TAG)
            }
            true
        }
        binding?.root?.click {
            if (binding?.bookDetail1?.isVisible == true) {
                updateBookDetailExpanded(false)
            } else {
                hide()
            }
            true
        }
    }

    private fun updateBookDetailExpanded(expanded: Boolean) {
        binding?.bookDetail1?.isVisible = expanded
        binding?.bookDetail2?.isVisible = expanded
    }

    private fun initCacheChapter() {
        binding?.cacheChapter?.click(10) {
            val isCaching = readerViewModel.chapterCache.value == true
            if (isCaching) {
                EventBus.post(EventKey.CHAPTER_LIST_CAHE)
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("缓存章节")
                    .setMessage("确定缓存点击“确定”按钮，否则点击“取消”")
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        EventBus.post(EventKey.CHAPTER_LIST_CAHE)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        readerViewModel?.chapterCache?.observe(viewLifecycleOwner) {
            val accent = R.color.dn_colorAccent.color(requireContext())
            val normal = R.color.dn_text_color_black.color(requireContext())
            binding?.cacheChapter?.imageTintList = ColorStateList.valueOf(if (it == true) accent else normal)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun refreshBookInfo() {
        val book = readerViewModel.book.value
        var bookTitle = book?.title?.ifBlank { "未知书籍" } ?: "未知书籍"
        bookTitle += colorText(">", R.color.colorPrimary.color(context))
        binding?.bookTitle?.text = bookTitle.htmlToSpanned()
        binding?.bookAuthor?.text = book?.author?.ifBlank { "佚名" } ?: "佚名"
        val chapterCount = book?.chapterList?.size ?: 0
        val readCount = (book?.record?.chapterIndex ?: 0) + 1
        val readChapter = book?.chapterList?.getOrNull(book.record?.chapterIndex ?: 0)
        binding?.bvUnread?.badgeCount = chapterCount - readCount
        binding?.bvUnread?.setHighlight(readChapter?.content?.firstOrNull()?.contentError != true)
        binding?.countChapter?.text = "共${chapterCount}章，已读${readCount}章，${chapterCount - readCount}章未读"
        binding?.lastChapter?.text = "最新：${book?.chapterList?.lastOrNull()?.title ?: "无"}"
        binding?.readChapter?.text = "已读：${readChapter?.title ?: "无"}"
        binding?.bookSource?.text = "${book?.bookSource?.name ?: "未知"}•共${book?.bookSourceCount ?: 0}个书源"
        val intro = (book?.record?.bookIntro ?: book?.intro).format(true)
        binding?.bookIntro?.text = "简介：\n${intro.ifBlank { "暂无简介" }}"
        binding?.bookCover?.glide(book?.record?.bookCover ?: book?.cover)
    }

    fun initScreenOrientationMode() {
        binding?.screenRotation?.click(10) {
            val options = arrayOf("跟随系统", "竖屏", "横屏", "横屏自动")
            val current = (globalConfig.readerOrientationMode.value ?: 0)
                .coerceIn(0, 3)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("屏幕旋转")
                .setSingleChoiceItems(options, current) { dialog, which ->
                    globalConfig.readerOrientationMode.postValue(which)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun initSettings() {
        binding?.settings?.click(10) {
            if (binding?.settingContainerFirst?.isVisible == true) {
                binding?.settingContainerFirst?.isVisible = false
                binding?.settingContainerSecond?.isVisible = true
            } else {
                binding?.settingContainerFirst?.isVisible = true
                binding?.settingContainerSecond?.isVisible = false
            }
        }
    }

    private fun initBrightnessSetting() {
        binding?.brightnessAuto?.click(10) {
            if (globalConfig.readerBrightnessPercent.value == -1) {
                globalConfig.readerBrightnessPercent.setValue(readSystemBrightnessPercent())
            } else {
                globalConfig.readerBrightnessPercent.setValue(-1)
            }
        }
        binding?.brightnessSeek?.setOnSeekBarChangeListener { _, progress, fromUser ->
            if (fromUser) {
                globalConfig.readerBrightnessPercent.setValue(progress.coerceIn(1, 255))
            }
        }
        globalConfig.readerBrightnessPercent.observe(viewLifecycleOwner) {
            updateBrightnessUi(it)
        }
    }

    private fun updateBrightnessUi(percent: Int) {
        val useSystemBrightness = percent < 0
        val accent = R.color.dn_colorAccent.color(requireContext())
        val normal = R.color.dn_text_color_black.color(requireContext())
        binding?.ivBrightnessAuto?.imageTintList = ColorStateList.valueOf(if (useSystemBrightness) accent else normal)
        binding?.brightnessSeek?.progressTintList = ColorStateList.valueOf(if (useSystemBrightness) normal else accent)
        binding?.brightnessSeek?.thumbTintList = ColorStateList.valueOf(if (useSystemBrightness) normal else accent)
        val brightnessPercent = readSystemBrightnessPercent()
        binding?.brightnessSeek?.progress = (if (useSystemBrightness) brightnessPercent else percent).coerceIn(1, 255)
    }

    private fun readSystemBrightnessPercent(): Int {
        val context = this.context ?: return -1
        return runCatching {
            val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            (brightness / 200f * 255f).coerceIn(1f, 255f).roundToInt()
        }.getOrElse { -1 }
    }

    private fun initBrowser() {
        binding?.browser?.click {
            EventBus.post(EventKey.BROWSER_OPEN)
        }
    }

    fun refreshChapterProgress() {
        binding?.chapterProgress?.max = pageLoader?.chapterCategory?.size ?: 0
        binding?.chapterProgress?.progress = (pageLoader?.chapterPos ?: 0) + 1
    }

    private fun initChapterList() {
        binding?.chapterLast?.click {
            pageLoader?.skipPreChapter()
            refreshChapterProgress()
        }
        binding?.chapterNext?.click {
            pageLoader?.skipNextChapter()
            refreshChapterProgress()
        }
        refreshChapterProgress()
        binding?.chapterList?.click {
            EventBus.post(EventKey.CHAPTER_LIST)
            hide()
        }
    }

    private fun initSpeak() {
        ttsUtil?.isSpeaking?.observe(viewLifecycleOwner, Observer { isSpeaking ->
            if (isSpeaking) {
                binding?.speak?.imageTintList = ColorStateList.valueOf(R.color.dn_colorAccent.color(requireContext()))
            } else {
                binding?.speak?.imageTintList = ColorStateList.valueOf(R.color.dn_text_color_black.color(requireContext()))
            }
        })
        binding?.speak?.click {
            EventBus.post(EventKey.CHAPTER_SPEAK)
        }
    }

    private fun initChapterError() {
        binding?.chapterError?.click {
            EventBus.post(EventKey.CHAPTER_CONTENT_ERROR)
        }
    }

    private fun initChapterSync() {
        binding?.chapterSync?.click {
            EventBus.post(EventKey.CHAPTER_SYNC_FORCE)
        }
    }

    private fun initPageModel() {
        val views = listOf(
            binding!!.pageModelSimulation,
            binding!!.pageModelCover,
            binding!!.pageModelSlide,
            binding!!.pageModelNone,
            binding!!.pageModelScroll
        )
        val view = views[globalConfig.readerPageMode.value!!]
        setSelected(view, true)
        views.forEachIndexed { index, view ->
            view.click {
                if (index != globalConfig.readerPageMode.value!!) {
                    setSelected(views[globalConfig.readerPageMode.value!!], false)
                    setSelected(view, true)
                    globalConfig.readerPageMode.postValue(index)
                }
            }
        }

    }

    private fun setSelected(view: SuperTextView, isSelected: Boolean) {
        if (isSelected) {
            view.solid = R.color.dn_color_primary.color(requireContext())
            view.setTextColor(R.color.mdr_grey_100.color(requireContext()))
        } else {
            view.solid = R.color.dn_background.color(requireContext())
            view.setTextColor(R.color.dn_text_color_black.color(requireContext()))
        }
    }

    private fun initDayNight() {
        if (globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_NO) {
            binding?.ivDayNight?.setImageResource(R.drawable.ic_theme_dark_24px)
        } else {
            binding?.ivDayNight?.setImageResource(R.drawable.ic_theme_light_24px)
        }
        binding?.dayNight?.click {
            when (globalConfig.appDayNightMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> {
//                    day_night.setImageResource(R.drawable.ic_theme_light_24px)
                    globalConfig.appDayNightMode = AppCompatDelegate.MODE_NIGHT_YES
                    //切换成深色模式。阅读器样式自动调整为上一次的深色样式
                    globalConfig.readerPageStyle.setValue(globalConfig.lastDarkTheme.value!!)
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }

                else -> {
//                    day_night.setImageResource(R.drawable.ic_theme_dark_24px)
                    globalConfig.appDayNightMode = AppCompatDelegate.MODE_NIGHT_NO
                    //切换成浅色模式。阅读器样式自动调整为上一次的浅色样式
                    globalConfig.readerPageStyle.setValue(globalConfig.lastLightTheme.value!!)
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }

        }
    }

    private fun initFontSize() {
        globalConfig.readerFontSize.observe(viewLifecycleOwner, Observer {
            binding?.fontText?.text = it.toString()
        })
        binding?.fontDecrease?.click(10) {
            globalConfig.readerFontSize.postValue(globalConfig.readerFontSize.value!! - 1)
        }
        binding?.fontIncrease?.click(10) {
            globalConfig.readerFontSize.postValue(globalConfig.readerFontSize.value!! + 1)
        }
    }

    private fun initLineSpacing() {
        globalConfig.readerLineSpacing.observe(viewLifecycleOwner, Observer {
            binding?.lineSpacingText?.text = "$it"
        })
        binding?.lineSpacingDecrease?.click(10) {
            globalConfig.readerLineSpacing.postValue(globalConfig.readerLineSpacing.value!! - 1)
        }
        binding?.lineSpacingIncrease?.click(10) {
            globalConfig.readerLineSpacing.postValue(globalConfig.readerLineSpacing.value!! + 1)
        }

        globalConfig.readerParaSpacing.observe(viewLifecycleOwner, Observer {
            binding?.paraSpacingText?.text = "$it"
        })
        binding?.paraSpacingDecrease?.click(10) {
            globalConfig.readerParaSpacing.postValue(globalConfig.readerParaSpacing.value!! - 1)
        }
        binding?.paraSpacingIncrease?.click(10) {
            globalConfig.readerParaSpacing.postValue(globalConfig.readerParaSpacing.value!! + 1)
        }

        globalConfig.readerLetterSpacing.observe(viewLifecycleOwner, Observer {
            binding?.letterSpacingText?.text = "$it"
        })
        binding?.letterSpacingDecrease?.click(10) {
            globalConfig.readerLetterSpacing.postValue(globalConfig.readerLetterSpacing.value!! - 1)
        }
        binding?.letterSpacingIncrease?.click(10) {
            globalConfig.readerLetterSpacing.postValue(globalConfig.readerLetterSpacing.value!! + 1)
        }
    }

    private fun initPageStyle() {
        binding?.pageStyleImport?.click {
            hide()
            DefaultPageStyleListFragment().show(parentFragmentManager, "DefaultPageStyleListFragment")
        }
        val adapter = PageStyleAdapter(this)
        adapter.itemLongClickListener = {
            val pageStyle = adapter.data[it]
            if (pageStyle is CustomPageStyle && binding?.pageStyleList?.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                hide()
                val isDeleteable = adapter.data.filter { it.isDark == pageStyle.isDark }.size > 1
                pageStyle.info.isDeleteable = isDeleteable
                CustomPageStyleFragment.newInstance(pageStyle.info)
                    .show(parentFragmentManager, "CustomPageStyleFragment")
            }
        }
        binding?.pageStyleList?.adapter = adapter
        var first = true
        PageStyle.styles.observe(viewLifecycleOwner) {
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
            if (first) {
                binding?.pageStyleList?.scrollToPosition(it.indexOfFirst { globalConfig.readerPageStyle.value!! == it.id })
                first = false
            }
        }
    }

    private fun initFontList() {
        initFontListData()
        binding?.fontList?.adapter = adapter
        binding?.fontImport?.click {
            //导入字体
            pickFontFile()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initFontListData() {
        globalConfig.readerFontFamily.observe(viewLifecycleOwner) {
            adapter.notifyDataSetChanged()
        }
        adapter.onLongClickListener = { fontInfo ->
            //长按删除自定义字体
            if (!fontInfo.isAsset) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除字体")
                    .setMessage("确定删除字体“${fontInfo.name}”吗？")
                    .setPositiveButton("删除") { _, _ ->
                        val file = File(fontInfo.path!!)
                        if (file.exists()) {
                            file.delete()
                            initFontListData()
                            if (globalConfig.readerFontFamily.value == fontInfo) {
                                globalConfig.readerFontFamily.postValue(FontInfo.DEFAULT)
                            }
                        } else {
                            toast("字体文件不存在，无法删除:${fontInfo.name}")
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                toast("内置字体无法删除:${fontInfo.name}")
            }

        }
        adapter.data = mutableListOf(
            FontInfo.DEFAULT,
            FontInfo("楷体", resId = R.font.fangzhengkaiti, isAsset = true)
        )
        val fontFiles = File(requireContext().filesDir, "font").listFiles()
        fontFiles?.forEach {
            if (it.isFile) {
                adapter.data.add(FontInfo(it.nameWithoutExtension, it.path))
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun pickFontFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
//            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-font-ttf", "application/x-font-otf"))
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_FONT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FONT && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                showImportFontNameDialog(uri)
            }
        }
    }

    private fun showImportFontNameDialog(uri: Uri) {
        val sourceFileName = resolveFontSourceFileName(uri)
        val defaultFontName = sourceFileName.substringBeforeLast('.', sourceFileName)
            .ifBlank { "自定义字体" }
        val editText = EditText(requireContext()).apply {
            setText(defaultFontName)
            setSelection(text?.length ?: 0)
            hint = "字体名称"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("设置字体名称")
            .setView(editText)
            .setPositiveButton("导入") { _, _ ->
                importFont(uri, editText.text?.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // 导入字体文件
    private fun importFont(uri: Uri, customFontName: String? = null) {
        try {
            Log.e("uri:$uri  uri.lastPathSegment:${uri.lastPathSegment}")
            val contentResolver = requireContext().contentResolver
            val sourceFileName = resolveFontSourceFileName(uri)
            val defaultFontName = sourceFileName.substringBeforeLast('.', sourceFileName)
                .ifBlank { "自定义字体" }
            val extension = sourceFileName.substringAfterLast('.', "")
                .takeIf { sourceFileName.contains('.') && it.isNotBlank() }
            val fontName = sanitizeFontName(
                customFontName
                    ?.trim()
                    ?.substringBeforeLast('.', customFontName.trim())
                    .takeUnless { it.isNullOrBlank() }
                    ?: defaultFontName
            )
            val targetFileName = if (extension != null) "$fontName.$extension" else fontName
            val fontFile = File(requireContext().filesDir, "font/$targetFileName")
            fontFile.parentFile?.mkdirs()
            if (fontFile.exists()) {
                fontFile.delete()
            }
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(fontFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("无法读取字体文件")
            Typeface.createFromFile(fontFile)
            // 现在你可以使用这个Typeface对象了
            initFontListData()
        } catch (e: Exception) {
            Log.e("导入字体失败:${e.message}", e)
            toast("导入字体失败:${e.message}")
        }
    }

    private fun resolveFontSourceFileName(uri: Uri): String {
        requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "font.ttf"
    }

    private fun sanitizeFontName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "自定义字体" }
    }

    private fun hide() {
        settingPopupWindow?.dismiss()
        parentFragmentManager.beginTransaction().hide(this).commitAllowingStateLoss()
    }

    override fun onDestroyView() {
        settingPopupWindow?.dismiss()
        settingPopupWindow = null
        binding = null
        super.onDestroyView()
    }

    private sealed class ReaderSettingPopupItem {
        data class Action(
            val title: String,
            val onClick: (Action) -> Unit,
        ) : ReaderSettingPopupItem()

        data class Detail(
            val title: String,
            var value: String,
            val onClick: (Detail) -> Unit,
        ) : ReaderSettingPopupItem()

        data class Switch(
            val title: String,
            var checked: Boolean,
            val onCheckedChange: (Switch, Boolean) -> Unit,
        ) : ReaderSettingPopupItem()

    }

    private class ReaderSettingPopupAdapter : RecyclerView.Adapter<ViewHolder>() {
        private val data = mutableListOf<ReaderSettingPopupItem>()

        fun submitList(items: List<ReaderSettingPopupItem>) {
            data.clear()
            data.addAll(items)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (data[position]) {
                is ReaderSettingPopupItem.Action -> 0
                is ReaderSettingPopupItem.Detail -> 1
                is ReaderSettingPopupItem.Switch -> 2
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return when (viewType) {
                2 -> SwitchViewHolder(
                    ItemReaderSettingPopupSwitchBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false,
                    ),
                )

                1 -> DetailViewHolder(
                    ItemReaderSettingPopupDetailBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false,
                    ),
                )

                else -> ActionViewHolder(
                    ItemReaderSettingPopupActionBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false,
                    ),
                )
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            when (val item = data[position]) {
                is ReaderSettingPopupItem.Action -> (holder as ActionViewHolder).bind(item)
                is ReaderSettingPopupItem.Detail -> (holder as DetailViewHolder).bind(item)
                is ReaderSettingPopupItem.Switch -> (holder as SwitchViewHolder).bind(item)
            }
        }

        override fun getItemCount(): Int = data.size

        private class ActionViewHolder(
            private val binding: ItemReaderSettingPopupActionBinding,
        ) : ViewHolder(binding.root) {
            fun bind(item: ReaderSettingPopupItem.Action) {
                binding.title.text = item.title
                binding.root.setOnClickListener { item.onClick(item) }
            }
        }

        private class DetailViewHolder(
            private val binding: ItemReaderSettingPopupDetailBinding,
        ) : ViewHolder(binding.root) {
            fun bind(item: ReaderSettingPopupItem.Detail) {
                binding.title.text = item.title
                binding.value.text = item.value
                binding.value.isVisible = item.value.isNotBlank()
                binding.root.setOnClickListener { item.onClick(item) }
            }
        }

        private class SwitchViewHolder(
            private val binding: ItemReaderSettingPopupSwitchBinding,
        ) : ViewHolder(binding.root) {
            fun bind(item: ReaderSettingPopupItem.Switch) {
                binding.title.text = item.title
                binding.switchView.setOnCheckedChangeListener(null)
                binding.switchView.isChecked = item.checked
                binding.root.setOnClickListener {
                    binding.switchView.isChecked = !binding.switchView.isChecked
                }
                binding.switchView.setOnCheckedChangeListener { _, isChecked ->
                    item.onCheckedChange(item, isChecked)
                }
            }
        }
    }

    class FontAdapter : BaseAdapter<FontInfo>(R.layout.item_font) {
        var onLongClickListener: ((FontInfo) -> Unit)? = null
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val binding = ItemFontBinding.bind(holder.itemView)
            holder.itemView.apply {
                val fontInfo = data[position]
                binding.fontText.text = fontInfo.name
                click {
                    globalConfig.readerFontFamily.postValue(fontInfo)
                }
                setOnLongClickListener {
                    onLongClickListener?.invoke(fontInfo)
                    true
                }

                if (globalConfig.readerFontFamily.value == fontInfo) {
                    binding.fontText.solid = R.color.dn_color_primary.color(context)
                    binding.fontText.setTextColor(R.color.mdr_grey_100.color(context))
                } else {
                    binding.fontText.solid = R.color.dn_background_dialog.color(context)
                    binding.fontText.setTextColor(R.color.dn_text_color_black.color(context))
                }
            }
        }
    }

    class PageStyleAdapter(val fragment: BookReaderSettingFragment) : RecyclerView.Adapter<ViewHolder>() {

        val data = mutableListOf<PageStyle>()
        var itemLongClickListener: ((Int) -> Unit)? = null

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val image = holder.itemView.findViewById<CircleImageView>(R.id.image)
            val pageStyle = data[position]
            (image.tag as? Job)?.cancel()
            val drawable =
                pageStyle.getBackgroundSync(fragment.requireContext(), 42.dp2Px, 32.dp2Px)
            if (drawable != null) {
                image.setImageDrawable(drawable)
            } else {
                val job = fragment.lifecycleScope.launch {
                    val background = withIo {
                        pageStyle.getBackground(image.context, 42.dp2Px, 32.dp2Px)
                    }
                    image.setImageDrawable(background)
                }
                image.tag = job
            }

            if (globalConfig.readerPageStyle.value != pageStyle.id) {
                image.borderColor = R.color.dn_text_color_black_disable.color(image.context)
            } else {
                image.borderColor = R.color.dn_color_primary.color(image.context)
            }
            holder.itemView.click {
                notifyDataSetChanged()
                globalConfig.readerPageStyle.postValue(pageStyle.id)
                //记录浅色 深色样式 和深色样式
                if (pageStyle.isDark) {
                    globalConfig.lastDarkTheme.postValue(pageStyle.id)
                } else {
                    globalConfig.lastLightTheme.postValue(pageStyle.id)
                }
            }
            holder.itemView.setOnLongClickListener {
                itemLongClickListener?.invoke(position)
                true
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return object : ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.reader_item_page_style, parent, false)
            ) {}
        }

        override fun getItemCount(): Int {
            return data.size
        }
    }

}
