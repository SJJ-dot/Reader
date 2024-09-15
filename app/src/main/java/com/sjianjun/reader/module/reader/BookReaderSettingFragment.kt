package com.sjianjun.reader.module.reader

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.coorchice.library.SuperTextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.FontInfo
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.toast
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.item_font.view.font_text
import kotlinx.android.synthetic.main.reader_fragment_setting_view.brightness_seek_bar
import kotlinx.android.synthetic.main.reader_fragment_setting_view.chapter_error
import kotlinx.android.synthetic.main.reader_fragment_setting_view.chapter_list
import kotlinx.android.synthetic.main.reader_fragment_setting_view.chapter_sync
import kotlinx.android.synthetic.main.reader_fragment_setting_view.day_night
import kotlinx.android.synthetic.main.reader_fragment_setting_view.download
import kotlinx.android.synthetic.main.reader_fragment_setting_view.font_decrease
import kotlinx.android.synthetic.main.reader_fragment_setting_view.font_import
import kotlinx.android.synthetic.main.reader_fragment_setting_view.font_increase
import kotlinx.android.synthetic.main.reader_fragment_setting_view.font_list
import kotlinx.android.synthetic.main.reader_fragment_setting_view.font_text
import kotlinx.android.synthetic.main.reader_fragment_setting_view.line_spacing_decrease
import kotlinx.android.synthetic.main.reader_fragment_setting_view.line_spacing_increase
import kotlinx.android.synthetic.main.reader_fragment_setting_view.line_spacing_text
import kotlinx.android.synthetic.main.reader_fragment_setting_view.page_model_cover
import kotlinx.android.synthetic.main.reader_fragment_setting_view.page_model_none
import kotlinx.android.synthetic.main.reader_fragment_setting_view.page_model_scroll
import kotlinx.android.synthetic.main.reader_fragment_setting_view.page_model_simulation
import kotlinx.android.synthetic.main.reader_fragment_setting_view.page_model_slide
import kotlinx.android.synthetic.main.reader_fragment_setting_view.page_style_import
import kotlinx.android.synthetic.main.reader_fragment_setting_view.page_style_list
import kotlinx.android.synthetic.main.reader_fragment_setting_view.speak
import kotlinx.android.synthetic.main.reader_item_page_style.view.image
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sjj.alog.Log
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.CustomPageStyleInfo
import sjj.novel.view.reader.page.PageStyle
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

/*
 * Created by shen jian jun on 2020-07-13
 */
class BookReaderSettingFragment : BottomSheetDialogFragment() {
    // 启动系统文件浏览器的请求码
    val adapter = FontAdapter()
    private val REQUEST_CODE_PICK_FONT = 1111
    override fun getTheme(): Int {
        return R.style.reader_setting_dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.reader_fragment_setting_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.window?.findViewById<View>(R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        initSpeak()
        initChapterList()
        initChapterError()
        initChapterSync()
        initDayNight()
        initBrightness()
        initFontSize()
        initLineSpacing()
        initPageStyle()
        initPageModel()
        initFontList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    private fun initChapterList() {
        download.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("缓存章节")
                .setMessage("确定缓存点击“确定”按钮，否则点击“取消”")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    EventBus.post(EventKey.CHAPTER_LIST_CAHE)
                    dismiss()
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        chapter_list.setOnClickListener {
            EventBus.post(EventKey.CHAPTER_LIST)
            dismiss()
        }
    }

    private fun initSpeak() {
        speak.setOnClickListener {
            EventBus.post(EventKey.CHAPTER_SPEAK)
            dismiss()
        }
    }

    private fun initChapterError() {
        chapter_error.setOnClickListener {
            EventBus.post(EventKey.CHAPTER_CONTENT_ERROR)
        }
    }

    private fun initChapterSync() {
        chapter_sync.setOnClickListener {
            EventBus.post(EventKey.CHAPTER_SYNC_FORCE)
        }
    }

    private fun initPageModel() {
        val views = listOf(
            page_model_simulation,
            page_model_cover,
            page_model_slide,
            page_model_none,
            page_model_scroll
        )
        val view = views[globalConfig.readerPageMode.value!!]
        setSelected(view, true)
        views.forEachIndexed { index, view ->
            view.setOnClickListener {
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
            day_night.setImageResource(R.drawable.ic_theme_dark_24px)
        } else {
            day_night.setImageResource(R.drawable.ic_theme_light_24px)
        }
        day_night.setOnClickListener {
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

    private fun initBrightness() {
        brightness_seek_bar.progress = Color.alpha(globalConfig.readerBrightnessMaskColor.value!!)
        brightness_seek_bar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                globalConfig.readerBrightnessMaskColor.postValue(Color.argb(progress, 0, 0, 0))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

    }

    private fun initFontSize() {
        globalConfig.readerFontSize.observe(viewLifecycleOwner, Observer {
            font_text.text = it.toString()
        })
        font_decrease.setOnClickListener {
            globalConfig.readerFontSize.postValue(globalConfig.readerFontSize.value!! - 1)
        }
        font_increase.setOnClickListener {
            globalConfig.readerFontSize.postValue(globalConfig.readerFontSize.value!! + 1)
        }
    }

    private fun initLineSpacing() {
        val decimalFormat = DecimalFormat("0.#")
        globalConfig.readerLineSpacing.observe(viewLifecycleOwner, Observer {
            line_spacing_text.text = decimalFormat.format(it)
        })
        line_spacing_decrease.setOnClickListener {
            globalConfig.readerLineSpacing.postValue(
                decimalFormat.format(globalConfig.readerLineSpacing.value!! - 0.1f).toFloat()
            )
        }
        line_spacing_increase.setOnClickListener {
            globalConfig.readerLineSpacing.postValue(
                decimalFormat.format(globalConfig.readerLineSpacing.value!! + 0.1f).toFloat()
            )
        }
    }

    private fun initPageStyle() {
        page_style_import.setOnClickListener {
            dismissAllowingStateLoss()
            CustomPageStyleFragment.newInstance(CustomPageStyleInfo().apply {
                ordinal = (PageStyle.customStyles.lastOrNull()?.ordinal ?: PageStyle.maxOrdinal) + 1
            }).show(parentFragmentManager, "CustomPageStyleFragment")
        }
        val adapter = Adapter(this)
        page_style_list.adapter = adapter
        var first = true
        globalConfig.customPageStyleInfoList.observe(viewLifecycleOwner) {
            adapter.data.clear()
            adapter.data.addAll(PageStyle.customStyles)
            adapter.data.addAll(PageStyle.defStyles)
            adapter.notifyDataSetChanged()
            if (first) {
                page_style_list.scrollToPosition(globalConfig.readerPageStyle.value!!)
                first = false
            }
        }
    }

    private fun initFontList() {
        initFontListData()
        font_list.adapter = adapter
        font_import.setOnClickListener {
            //导入字体
            pickFontFile()
        }
    }

    private fun initFontListData() {
        adapter.data = mutableListOf(
            FontInfo.DEFAULT,
            FontInfo("方正楷体", resId = R.font.fangzhengkaiti, isAsset = true)
        )
        val fontFiles = File(requireContext().filesDir, "font").listFiles()
        fontFiles?.forEach {
            if (it.isFile) {
                adapter.data.add(FontInfo(it.name, it.path))
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
                importFont(uri)
            }
        }
    }

    // 导入字体文件
    private fun importFont(uri: Uri) {
        try {
            Log.e("uri:$uri  uri.lastPathSegment:${uri.lastPathSegment}")
            val contentResolver = activity?.getContentResolver()!!
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = uri.lastPathSegment!!.split("/").last().split(".").first()
            val fontFile = File(requireContext().filesDir, "font/${fileName}")
            fontFile.deleteOnExit()
            fontFile.parentFile?.mkdirs()
            inputStream?.use { input ->
                FileOutputStream(fontFile).use { output ->
                    input.copyTo(output)
                }
            }
            val typeface = Typeface.createFromFile(fontFile)
            // 现在你可以使用这个Typeface对象了
            initFontListData()
            toast("导入字体成功:${fileName}")
        } catch (e: Exception) {
            Log.e("导入字体失败:${e.message}", e)
            toast("导入字体失败:${e.message}")
        }
    }

    class FontAdapter : BaseAdapter<FontInfo>(R.layout.item_font) {
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.itemView.apply {
                val fontInfo = data[position]
                font_text.text = fontInfo.name
                setOnClickListener {
                    globalConfig.readerFontFamily.postValue(fontInfo)
                    notifyDataSetChanged()
                }

                if (globalConfig.readerFontFamily.value == fontInfo) {
                    font_text.solid = R.color.dn_color_primary.color(context)
                    font_text.setTextColor(R.color.mdr_grey_100.color(context))
                } else {
                    font_text.solid = R.color.dn_background.color(context)
                    font_text.setTextColor(R.color.dn_text_color_black.color(context))
                }
            }
        }
    }

    class Adapter(val fragment: BookReaderSettingFragment) : RecyclerView.Adapter<ViewHolder>() {
        private val VIEW_TYPE_CUSTOM = 1
        private val VIEW_TYPE_SYS = 0


        val data = mutableListOf<PageStyle>()

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

            if (globalConfig.readerPageStyle.value != pageStyle.ordinal) {
                image.borderColor = R.color.dn_text_color_black_disable.color(image.context)
            } else {
                image.borderColor = R.color.dn_color_primary.color(image.context)
            }
            holder.itemView.setOnClickListener {
                notifyDataSetChanged()
                globalConfig.readerPageStyle.postValue(pageStyle.ordinal)
                //记录浅色 深色样式 和深色样式
                if (pageStyle.isDark || pageStyle.ordinal == 0 && globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
                    globalConfig.lastDarkTheme.postValue(pageStyle.ordinal)
                } else {
                    globalConfig.lastLightTheme.postValue(pageStyle.ordinal)
                }
            }
            holder.itemView.setOnLongClickListener {
                if (pageStyle is CustomPageStyle) {
                    fragment.dismissAllowingStateLoss()
                    CustomPageStyleFragment.newInstance(pageStyle.info)
                        .show(fragment.parentFragmentManager, "CustomPageStyleFragment")
                }
                true
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (data[position] is CustomPageStyle) {
                VIEW_TYPE_CUSTOM
            } else {
                VIEW_TYPE_SYS
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val res = if (viewType == VIEW_TYPE_CUSTOM) {
                R.layout.reader_item_custom_page_style
            } else {
                R.layout.reader_item_page_style
            }
            return object :
                ViewHolder(LayoutInflater.from(parent.context).inflate(res, parent, false)) {}
        }

        override fun getItemCount(): Int {
            return data.size
        }
    }

}