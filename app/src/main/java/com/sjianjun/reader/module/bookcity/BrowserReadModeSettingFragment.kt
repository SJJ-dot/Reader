package com.sjianjun.reader.module.bookcity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.FontInfo
import com.sjianjun.reader.databinding.BrowserReadModeSettingsBinding
import com.sjianjun.reader.databinding.PopupReaderSettingMenuBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.module.bookcity.BrowserReadModeActivity.Companion.curChapterData
import com.sjianjun.reader.module.reader.BookReaderSettingFragment
import com.sjianjun.reader.module.reader.CustomPageStyleFragment
import com.sjianjun.reader.module.reader.DefaultPageStyleListFragment
import com.sjianjun.reader.module.reader.ReaderClickAreaSettingDialogFragment
import com.sjianjun.reader.module.reader.ReplacementRuleListDialogFragment
import com.sjianjun.reader.module.reader.SettingTtsDialogFragment
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.TtsService
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.setTextColorRes
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.PageLoader
import sjj.novel.view.reader.page.PageMode
import sjj.novel.view.reader.page.PageStyle
import java.io.File
import kotlin.math.roundToInt

class BrowserReadModeSettingFragment : BaseFragment() {
    private var settingPopupWindow: android.widget.PopupWindow? = null
    private var binding: BrowserReadModeSettingsBinding? = null
    private var titleText: String = ""

    private val pageLoader
        get() = (activity as? BrowserReadModeActivity)?.pageLoader

    companion object {
        const val TAG = "BrowserReadModeSettingFragment"
    }

    fun setArgs(title: String) {
        this.titleText = title
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            binding?.settingSample?.isVisible = true
            binding?.settingDetail?.isVisible = false
            refreshNavAndProgress()
            refreshHistoryNavigationState()
            titleText = pageLoader?.curChapter?.title ?: titleText
            binding?.bookTitle?.text = titleText
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.browser_read_mode_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = BrowserReadModeSettingsBinding.bind(view)
        val b = binding!!

        val bottomSetting = b.settingBottomContainer
        val topBar = b.topBar
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.displayCutout
            val safeLeft = maxOf(systemBars.left, cutout?.safeInsetLeft ?: 0)
            val safeTop = maxOf(systemBars.top, cutout?.safeInsetTop ?: 0)
            val safeRight = maxOf(systemBars.right, cutout?.safeInsetRight ?: 0)
            val safeBottom = maxOf(systemBars.bottom, cutout?.safeInsetBottom ?: 0)
            topBar.setPadding(topBar.paddingLeft, maxOf(topBar.paddingTop, safeTop), topBar.paddingRight, topBar.paddingBottom)
            bottomSetting.setPadding(bottomSetting.paddingLeft, bottomSetting.paddingTop, bottomSetting.paddingRight, maxOf(safeBottom, bottomSetting.paddingBottom))
            b.root.setPadding(safeLeft, 0, safeRight, 0)
            insets
        }

        // 恢复标题：夜间模式切换导致 Activity 重建后 titleText 丢失，从 pageLoader 恢复
        titleText = pageLoader?.curChapter?.title ?: titleText
        b.bookTitle.text = titleText
        b.topBarBack.click(10) {
            (activity as? BrowserReadModeActivity)?.finish()
        }
        b.topBarSettingsMore.click(100) { v -> showPopupMenu(v) }
        b.topBarBrowser.click(10) {
            (activity as? BrowserReadModeActivity)?.openInBrowser(false)
        }
        b.topBarHome.click(10) {
            (activity as? BrowserReadModeActivity)?.openInBrowser(true)
        }

        // --- Bottom buttons ---
        // TTS
        TtsService.isSpeaking.observe(viewLifecycleOwner, Observer { isSpeaking ->
            val c = if (isSpeaking) R.color.dn_colorAccent else R.color.dn_text_color_black
            b.speak.imageTintList = android.content.res.ColorStateList.valueOf(c.color(requireContext()))
        })
        b.speak.click {
            if (TtsService.isSpeaking.value == true) {
                TtsService.stop()
                pageLoader?.setTtsSpeakLine(null)
                return@click
            }
            if (parentFragmentManager.findFragmentByTag(SettingTtsDialogFragment.TAG) == null) {
                SettingTtsDialogFragment().show(parentFragmentManager, SettingTtsDialogFragment.TAG)
                hideSelf()
            }
        }

        b.settings.click(10) {
            if (b.settingSample.isVisible) {
                b.settingSample.isVisible = false
                b.settingDetail.isVisible = true
            } else {
                b.settingSample.isVisible = true
                b.settingDetail.isVisible = false
            }
        }

        initDayNight()
        initRefresh()
        initToggleWebView()
        initHistoryNavigation()
        initScreenOrientationMode()
        initFontSize()
        initLineSpacing()
        initPageStyle()
        initPageModel()
        initFontList()
        initChapterNav()
        initBrightnessSetting()
        applyAllSettings()
        refreshNavAndProgress()

        b.root.click { hideSelf() }

        setOnBackPressed {
            if (isVisible) {
                hideSelf()
                true
            } else {
                false
            }
        }
    }

    private fun applyAllSettings() {
        val loader = pageLoader ?: return
        loader.setPageStyle(PageStyle.getStyle(globalConfig.readerPageStyle.value))
        loader.setPageMode(PageMode.entries.getOrNull(globalConfig.readerPageMode.value ?: 0) ?: PageMode.SIMULATION)
        val ts = (globalConfig.readerFontSize.value ?: 0).toFloat()
        val ls = (globalConfig.readerLineSpacing.value ?: 0).toFloat()
        val ps = (globalConfig.readerParaSpacing.value ?: 0).toFloat()
        val lts = (globalConfig.readerLetterSpacing.value ?: 0).toFloat()
        if (ts > 0f) loader.setTextSize(ts, ls, ps, lts / 100f)
    }

    private fun initChapterNav() {
        val b = binding ?: return
        b.chapterDirectory.click {
            (activity as? BrowserReadModeActivity)?.openDrawer()
            hideSelf()
        }
        b.chapterLast.click {
            if (b.chapterLast.isEnabled) {
                (activity as? BrowserReadModeActivity)?.gotoPrevChapter()
            }
        }
        b.chapterNext.click {
            if (b.chapterNext.isEnabled) {
                (activity as? BrowserReadModeActivity)?.gotoNextChapter()
            }
        }
    }

    fun refreshNavAndProgress() {
        val b = binding ?: return
        val activity = activity as? BrowserReadModeActivity ?: return
        val loader = pageLoader ?: return
        titleText = loader.curChapter?.title ?: titleText
        b.bookTitle.text = titleText
        b.chapterLast.text = "上一章"
        b.chapterNext.text = "下一章"
        val curPage = loader.pagePos + 1
        val totalPages = loader.curPageList?.size ?: 0
        b.chapterProgress.max = totalPages
        b.chapterProgress.progress = curPage
    }

    private fun initDayNight() {
        val b = binding ?: return
        if (globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_NO) {
            b.dayNight.setImageResource(R.drawable.ic_theme_dark_24px)
        } else {
            b.dayNight.setImageResource(R.drawable.ic_theme_light_24px)
        }
        b.dayNight.click {
            when (globalConfig.appDayNightMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> {
                    globalConfig.appDayNightMode = AppCompatDelegate.MODE_NIGHT_YES
                    globalConfig.readerPageStyle.setValue(globalConfig.lastDarkTheme.value!!)
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }

                else -> {
                    globalConfig.appDayNightMode = AppCompatDelegate.MODE_NIGHT_NO
                    globalConfig.readerPageStyle.setValue(globalConfig.lastLightTheme.value!!)
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
        }
    }

    private fun initRefresh() {
        val b = binding ?: return
        b.refresh.click {
            val act = activity as? BrowserReadModeActivity ?: return@click
            if (b.ivRefresh.isSelected) {
                // 当前正在加载 → 停止
                act.stopHiddenLoading()
            } else {
                act.refresh()
                hideSelf()
            }
        }
        (activity as? BrowserReadModeActivity)?.isHiddenLoading?.observe(viewLifecycleOwner) { loading ->
            b.ivRefresh.isSelected = loading == true
        }
    }

    private fun initToggleWebView() {
        val b = binding ?: return
        b.toggleWebView.click(10) {
            (activity as? BrowserReadModeActivity)?.toggleHiddenWebViewVisible()
            hideSelf()
        }
    }

    private fun initHistoryNavigation() {
        val b = binding ?: return
        b.historyBackward.click(10) {
            (activity as? BrowserReadModeActivity)?.goBackReadModeHistory()
            refreshHistoryNavigationState()
        }
        b.historyForward.click(10) {
            (activity as? BrowserReadModeActivity)?.goForwardReadModeHistory()
            refreshHistoryNavigationState()
        }
        refreshHistoryNavigationState()
    }

    private fun refreshHistoryNavigationState() {
        val b = binding ?: return
        val act = activity as? BrowserReadModeActivity
        val canBack = act?.canGoBackReadModeHistory() == true
        val canForward = act?.canGoForwardReadModeHistory() == true
        b.historyBackward.isEnabled = canBack
        b.historyForward.isEnabled = canForward
        b.ivHistoryBackward.isEnabled = canBack
        b.ivHistoryForward.isEnabled = canForward
    }

    private fun initBrightnessSetting() {
        val b = binding ?: return
        b.brightnessAuto.click(10) {
            if (globalConfig.readerBrightnessPercent.value == -1) {
                globalConfig.readerBrightnessPercent.setValue(readSystemBrightnessPercent())
            } else {
                globalConfig.readerBrightnessPercent.setValue(-1)
            }
        }
        b.brightnessSeek.setOnSeekBarChangeListener { _, progress, fromUser ->
            if (fromUser) {
                globalConfig.readerBrightnessPercent.setValue(progress.coerceIn(1, 255))
            }
        }
        globalConfig.readerBrightnessPercent.observe(viewLifecycleOwner) {
            updateBrightnessUi(it)
        }
        updateBrightnessUi(globalConfig.readerBrightnessPercent.value ?: -1)

        b.screenKeepOn.click(10) {
            val keepOn = !(globalConfig.readerScreenKeepOn.value ?: false)
            globalConfig.readerScreenKeepOn.postValue(keepOn)
            toast(if (keepOn) "已开启阅读时屏幕常亮" else "已关闭阅读时屏幕常亮")
        }
        globalConfig.readerScreenKeepOn.observe(viewLifecycleOwner) {
            val accent = R.color.dn_colorAccent.color(requireContext())
            val normal = R.color.dn_text_color_black_secondary.color(requireContext())
            b.ivScreenKeepOn.imageTintList = android.content.res.ColorStateList.valueOf(if (it) accent else normal)
        }
    }

    private fun updateBrightnessUi(percent: Int) {
        val b = binding ?: return
        val useSystemBrightness = percent < 0
        val accent = R.color.dn_colorAccent.color(requireContext())
        val normal = R.color.dn_text_color_black.color(requireContext())
        b.ivBrightnessAuto.imageTintList = android.content.res.ColorStateList.valueOf(if (useSystemBrightness) accent else normal)
        b.brightnessSeek.progressTintList = android.content.res.ColorStateList.valueOf(if (useSystemBrightness) normal else accent)
        b.brightnessSeek.thumbTintList = android.content.res.ColorStateList.valueOf(if (useSystemBrightness) normal else accent)
        val brightnessPercent = readSystemBrightnessPercent()
        b.brightnessSeek.progress = (if (useSystemBrightness) brightnessPercent else percent).coerceIn(1, 255)
    }

    private fun readSystemBrightnessPercent(): Int {
        val context = this.context ?: return -1
        return runCatching {
            val brightness = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
            (brightness / 200f * 255f).coerceIn(1f, 255f).roundToInt()
        }.getOrElse { -1 }
    }

    private fun initScreenOrientationMode() {
        val b = binding ?: return
        b.screenRotation.click(10) {
            val options = arrayOf("跟随系统", "竖屏", "横屏", "横屏自动")
            val current = (globalConfig.readerOrientationMode.value ?: 0).coerceIn(0, 3)
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

    private fun initFontSize() {
        val b = binding ?: return
        globalConfig.readerFontSize.observe(viewLifecycleOwner) { b.fontText.text = it.toString() }
        b.fontDecrease.click(10) { globalConfig.readerFontSize.postValue(globalConfig.readerFontSize.value!! - 1) }
        b.fontIncrease.click(10) { globalConfig.readerFontSize.postValue(globalConfig.readerFontSize.value!! + 1) }
    }

    private fun initLineSpacing() {
        val b = binding ?: return
        globalConfig.readerLineSpacing.observe(viewLifecycleOwner) { b.lineSpacingText.text = "$it" }
        b.lineSpacingDecrease.click(10) { globalConfig.readerLineSpacing.postValue(globalConfig.readerLineSpacing.value!! - 1) }
        b.lineSpacingIncrease.click(10) { globalConfig.readerLineSpacing.postValue(globalConfig.readerLineSpacing.value!! + 1) }
        globalConfig.readerParaSpacing.observe(viewLifecycleOwner) { b.paraSpacingText.text = "$it" }
        b.paraSpacingDecrease.click(10) { globalConfig.readerParaSpacing.postValue(globalConfig.readerParaSpacing.value!! - 1) }
        b.paraSpacingIncrease.click(10) { globalConfig.readerParaSpacing.postValue(globalConfig.readerParaSpacing.value!! + 1) }
        globalConfig.readerLetterSpacing.observe(viewLifecycleOwner) { b.letterSpacingText.text = "$it" }
        b.letterSpacingDecrease.click(10) { globalConfig.readerLetterSpacing.postValue(globalConfig.readerLetterSpacing.value!! - 1) }
        b.letterSpacingIncrease.click(10) { globalConfig.readerLetterSpacing.postValue(globalConfig.readerLetterSpacing.value!! + 1) }
    }

    private fun initPageStyle() {
        val b = binding ?: return
        b.pageStyleImport.click {
            hideSelf()
            DefaultPageStyleListFragment().show(parentFragmentManager, "DefaultPageStyleListFragment")
        }
        val adapter = BookReaderSettingFragment.PageStyleAdapter(this)
        adapter.itemLongClickListener = {
            val pageStyle = adapter.data[it]
            if (pageStyle is CustomPageStyle && b.pageStyleList.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                hideSelf()
                val isDeleteable = adapter.data.filter { ps -> ps.isDark == pageStyle.isDark }.size > 1
                pageStyle.info.isDeleteable = isDeleteable
                CustomPageStyleFragment.newInstance(pageStyle.info)
                    .show(parentFragmentManager, "CustomPageStyleFragment")
            }
        }
        b.pageStyleList.adapter = adapter
        var first = true
        PageStyle.styles.observe(viewLifecycleOwner) {
            adapter.data.clear()
            adapter.data.addAll(it)
            adapter.notifyDataSetChanged()
            if (first) {
                b.pageStyleList.scrollToPosition(it.indexOfFirst { globalConfig.readerPageStyle.value!! == it.id })
                first = false
            }
        }
    }

    private fun initPageModel() {
        val b = binding ?: return
        val views = listOf(b.pageModelSimulation, b.pageModelCover, b.pageModelSlide, b.pageModelNone, b.pageModelScroll)
        setSelected(views[globalConfig.readerPageMode.value!!], true)
        views.forEachIndexed { index, v ->
            v.click {
                if (index != globalConfig.readerPageMode.value!!) {
                    setSelected(views[globalConfig.readerPageMode.value!!], false)
                    setSelected(v, true)
                    globalConfig.readerPageMode.postValue(index)
                }
            }
        }
    }

    private fun setSelected(view: View, selected: Boolean) {
        if (view is com.coorchice.library.SuperTextView) {
            view.setTextColor(if (selected) R.color.mdr_white.color(requireContext()) else R.color.dn_text_color_black.color(requireContext()))
            view.setSolid(if (selected) R.color.colorPrimary.color(requireContext()) else R.color.dn_background_dialog.color(requireContext()))
        }
    }

    private val fontAdapter = BookReaderSettingFragment.FontAdapter()

    private fun initFontList() {
        val b = binding ?: return
        initFontListData()
        b.fontList.adapter = fontAdapter
        b.fontImport.click { pickFontFile() }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initFontListData() {
        globalConfig.readerFontFamily.observe(viewLifecycleOwner) { fontAdapter.notifyDataSetChanged() }
        fontAdapter.onLongClickListener = { fontInfo ->
            if (!fontInfo.isAsset) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除字体")
                    .setMessage("确定删除字体\"${fontInfo.name}\"吗？")
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
        fontAdapter.data = mutableListOf(
            FontInfo.DEFAULT,
            FontInfo("楷体", resId = R.font.fangzhengkaiti, isAsset = true)
        )
        val fontFiles = File(requireContext().filesDir, "font").listFiles()
        fontFiles?.forEach {
            if (it.isFile) {
                fontAdapter.data.add(FontInfo(it.nameWithoutExtension, it.path))
            }
        }
        fontAdapter.notifyDataSetChanged()
    }

    private val REQUEST_CODE_PICK_FONT = 10001

    private fun pickFontFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_FONT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FONT && resultCode == android.app.Activity.RESULT_OK) {
            data?.data?.let { uri -> showImportFontNameDialog(uri) }
        }
    }

    private fun showImportFontNameDialog(uri: Uri) {
        val defaultName = resolveFontSourceFileName(uri)
        val editText = android.widget.EditText(requireContext()).apply {
            setText(defaultName)
            setSelection(text?.length ?: 0)
            hint = "字体名称"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
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

    private fun resolveFontSourceFileName(uri: Uri): String {
        requireContext().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) return it.getString(idx)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "font.ttf"
    }

    private fun importFont(uri: Uri, customFontName: String?) {
        val fontName = customFontName?.trim()?.takeIf { it.isNotBlank() } ?: "自定义字体"
        lifecycleScope.launch {
            try {
                val fontDir = File(requireContext().filesDir, "font")
                if (!fontDir.exists()) fontDir.mkdirs()
                val sourceName = resolveFontSourceFileName(uri)
                val ext = sourceName.substringAfterLast(".", "ttf")
                val destFile = File(fontDir, "$fontName.$ext")
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                globalConfig.readerFontFamily.postValue(
                    FontInfo(name = fontName, path = destFile.absolutePath, isAsset = false, resId = 0)
                )
                initFontListData()
                toast("字体导入成功")
            } catch (e: Exception) {
                Log.e("字体导入失败", e)
                toast("字体导入失败")
            }
        }
    }

    private fun showPopupMenu(anchor: View) {
        val popupAdapter = BookReaderSettingFragment.ReaderSettingPopupAdapter()
        val popupBinding = PopupReaderSettingMenuBinding.inflate(layoutInflater)
        popupBinding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        popupBinding.recyclerView.adapter = popupAdapter
        val popupItems = buildPopupItems(popupAdapter)
        popupAdapter.submitList(popupItems)

        val contentHeight = popupItems.size * 40.dp2Px + 12.dp2Px
        val popupWidth = maxOf(220.dp2Px, anchor.width)
        val popupHeight = minOf(contentHeight, (resources.displayMetrics.heightPixels * 0.5f).toInt())
        popupBinding.root.layoutParams = RecyclerView.LayoutParams(popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        popupBinding.recyclerView.layoutParams = popupBinding.recyclerView.layoutParams.apply { width = popupWidth; height = popupHeight }
        popupBinding.recyclerView.minimumHeight = popupHeight
        popupBinding.recyclerView.requestLayout()

        val popupWindow = android.widget.PopupWindow(popupBinding.root, popupWidth, popupHeight, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            elevation = 8.dp2Px.toFloat()
        }
        val xOff = anchor.width - popupWidth
        popupWindow.showAsDropDown(anchor, xOff, 8.dp2Px)
        settingPopupWindow = popupWindow
    }

    private fun buildPopupItems(adapter: BookReaderSettingFragment.ReaderSettingPopupAdapter): List<BookReaderSettingFragment.ReaderSettingPopupItem> {
        val jianFanMode = (globalConfig.readerJianFanMode.value ?: PageLoader.MODE_JIAN_FAN_OFF).coerceIn(PageLoader.MODE_JIAN_FAN_OFF, PageLoader.MODE_FAN_TO_JIAN)
        val typesettingMode = (globalConfig.readerTypesettingMode.value ?: PageLoader.MODE_TYPESETTING_HORIZONTAL_LTR).coerceIn(PageLoader.MODE_TYPESETTING_HORIZONTAL_LTR, PageLoader.MODE_TYPESETTING_VERTICAL_RTL)
        return listOf(
            BookReaderSettingFragment.ReaderSettingPopupItem.Detail(title = "简繁转换", value = arrayOf("关闭", "简体转繁体", "繁体转简体")[jianFanMode]) { item ->
                val options = arrayOf("关闭", "简体转繁体", "繁体转简体")
                val current = (globalConfig.readerJianFanMode.value ?: PageLoader.MODE_JIAN_FAN_OFF).coerceIn(PageLoader.MODE_JIAN_FAN_OFF, PageLoader.MODE_FAN_TO_JIAN)
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
            BookReaderSettingFragment.ReaderSettingPopupItem.Detail(title = "文字排版", value = arrayOf("横排左起", "横排右起", "竖排左起", "竖排右起")[typesettingMode]) { item ->
                val options = arrayOf("横排左起", "横排右起", "竖排左起", "竖排右起")
                val current = (globalConfig.readerTypesettingMode.value ?: PageLoader.MODE_TYPESETTING_HORIZONTAL_LTR).coerceIn(PageLoader.MODE_TYPESETTING_HORIZONTAL_LTR, PageLoader.MODE_TYPESETTING_VERTICAL_RTL)
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
            BookReaderSettingFragment.ReaderSettingPopupItem.Action(title = "点击区域") {
                hideSelf()
                if (parentFragmentManager.findFragmentByTag(ReaderClickAreaSettingDialogFragment.TAG) == null) {
                    ReaderClickAreaSettingDialogFragment().show(parentFragmentManager, ReaderClickAreaSettingDialogFragment.TAG)
                }
            },
            BookReaderSettingFragment.ReaderSettingPopupItem.Action(title = "净化替换") {
                hideSelf()
                if (parentFragmentManager.findFragmentByTag(ReplacementRuleListDialogFragment.TAG) == null) {
                    // 默认作用域：当前阅读页的主域名（缺省时回退到 WebBook.url 主域）
                    val curUrl = pageLoader?.curChapter?.link
                    val defaultScope = curUrl?.toHttpUrlOrNull()?.topPrivateDomain()
                    // 当前阅读上下文：书名 + URL + 章节标题，用于在列表里识别作用域是否命中
                    val activeScopeText = listOf(
                        pageLoader?.mCollBook?.title.orEmpty(),
                        pageLoader?.mCollBook?.url.orEmpty(),
                        curUrl.orEmpty(),
                        pageLoader?.curChapter?.title.orEmpty(),
                    ).joinToString("\n")
                    ReplacementRuleListDialogFragment.newInstance(defaultScope, activeScopeText)
                        .show(parentFragmentManager, ReplacementRuleListDialogFragment.TAG)
                }
            },
            BookReaderSettingFragment.ReaderSettingPopupItem.Switch(title = "音量键翻页", checked = globalConfig.readerVolumeKeyPageTurn.value == true) { item, checked ->
                item.checked = checked
                globalConfig.readerVolumeKeyPageTurn.postValue(checked)
            },
        )
    }

    private fun hideSelf() {
        settingPopupWindow?.dismiss()
        parentFragmentManager.beginTransaction().hide(this).commitAllowingStateLoss()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
