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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.postDelayed
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.coorchice.library.SuperTextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.FontInfo
import com.sjianjun.reader.databinding.ItemFontBinding
import com.sjianjun.reader.databinding.ReaderFragmentSettingViewBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sjj.alog.Log
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.PageStyle
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

/*
 * Created by shen jian jun on 2020-07-13
 */
class BookReaderSettingFragment : BottomSheetDialogFragment() {
    var binding: ReaderFragmentSettingViewBinding? = null

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
        binding = ReaderFragmentSettingViewBinding.bind(view)
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
        initBrowser()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    private fun initBrowser(){
        binding?.browser?.click {
            EventBus.post(EventKey.BROWSER_OPEN)
            dismiss()
        }
    }
    private fun initChapterList() {
        binding?.download?.click {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("缓存章节")
                .setMessage("确定缓存点击“确定”按钮，否则点击“取消”")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    EventBus.post(EventKey.CHAPTER_LIST_CAHE)
                    dismiss()
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        binding?.chapterList?.click {
            EventBus.post(EventKey.CHAPTER_LIST)
            dismiss()
        }
    }

    private fun initSpeak() {
        binding?.speak?.click {
            EventBus.post(EventKey.CHAPTER_SPEAK)
            dismiss()
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
            binding?.dayNight?.setImageResource(R.drawable.ic_theme_dark_24px)
        } else {
            binding?.dayNight?.setImageResource(R.drawable.ic_theme_light_24px)
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

    private fun initBrightness() {
        binding?.brightnessSeekBar?.progress = Color.alpha(globalConfig.readerBrightnessMaskColor.value!!)
        binding?.brightnessSeekBar?.setOnSeekBarChangeListener(object :
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
        val decimalFormat = DecimalFormat("0.#")
        globalConfig.readerLineSpacing.observe(viewLifecycleOwner, Observer {
            binding?.lineSpacingText?.text = decimalFormat.format(it)
        })
        binding?.lineSpacingDecrease?.click(10) {
            globalConfig.readerLineSpacing.postValue(
                decimalFormat.format(globalConfig.readerLineSpacing.value!! - 0.1f).toFloat()
            )
        }
        binding?.lineSpacingIncrease?.click(10) {
            globalConfig.readerLineSpacing.postValue(
                decimalFormat.format(globalConfig.readerLineSpacing.value!! + 0.1f).toFloat()
            )
        }
    }

    private fun initPageStyle() {
        if (PageStyle.getStyle(globalConfig.readerPageStyle.value).isDark) {
            ImmersionBar.with(this).statusBarDarkFont(false).init()
        } else {
            ImmersionBar.with(this).statusBarDarkFont(true).init()
        }
        binding?.pageStyleImport?.click {
            dismissAllowingStateLoss()
            DefaultPageStyleListFragment().show(parentFragmentManager, "DefaultPageStyleListFragment")
        }
        val adapter = Adapter(this)
        adapter.itemLongClickListener = {
            val pageStyle = adapter.data[it]
            if (pageStyle is CustomPageStyle && binding?.pageStyleList?.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                dismissAllowingStateLoss()
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
            val binding = ItemFontBinding.bind(holder.itemView)
            holder.itemView.apply {
                val fontInfo = data[position]
                binding.fontText.text = fontInfo.name
                click {
                    globalConfig.readerFontFamily.postValue(fontInfo)
                    notifyDataSetChanged()
                }

                if (globalConfig.readerFontFamily.value == fontInfo) {
                    binding.fontText.solid = R.color.dn_color_primary.color(context)
                    binding.fontText.setTextColor(R.color.mdr_grey_100.color(context))
                } else {
                    binding.fontText.solid = R.color.dn_background.color(context)
                    binding.fontText.setTextColor(R.color.dn_text_color_black.color(context))
                }
            }
        }
    }

    class Adapter(val fragment: BookReaderSettingFragment) : RecyclerView.Adapter<ViewHolder>() {

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
                if (pageStyle.isDark) {
                    ImmersionBar.with(fragment).statusBarDarkFont(false).init()
                } else {
                    ImmersionBar.with(fragment).statusBarDarkFont(true).init()
                }
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