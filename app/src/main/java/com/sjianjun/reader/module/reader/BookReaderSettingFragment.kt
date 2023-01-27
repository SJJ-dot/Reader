package com.sjianjun.reader.module.reader

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.coorchice.library.SuperTextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.coroutine.launchIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import kotlinx.android.synthetic.main.reader_fragment_setting_view.*
import kotlinx.android.synthetic.main.reader_item_page_style.view.*
import sjj.novel.view.reader.page.PageStyle
import java.text.DecimalFormat

/*
 * Created by shen jian jun on 2020-07-13
 */
class BookReaderSettingFragment : BottomSheetDialogFragment() {

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
        initChapterList()
        initChapterError()
        initChapterSync()
        initDayNight()
        initBrightness()
        initFontSize()
        initLineSpacing()
        initPageStyle()
        initPageModel()
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
                    day_night.setImageResource(R.drawable.ic_theme_light_24px)
                    globalConfig.appDayNightMode = AppCompatDelegate.MODE_NIGHT_YES
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    //切换成深色模式。阅读器样式自动调整为上一次的深色样式
                    globalConfig.readerPageStyle.postValue(globalConfig.lastDarkTheme.value)
                }
                else -> {
                    day_night.setImageResource(R.drawable.ic_theme_dark_24px)
                    globalConfig.appDayNightMode = AppCompatDelegate.MODE_NIGHT_NO
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    //切换成浅色模式。阅读器样式自动调整为上一次的浅色样式
                    globalConfig.readerPageStyle.postValue(globalConfig.lastLightTheme.value)
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
        val adapter = Adapter(this)
        page_style_list.adapter = adapter
        launchIo {
            PageStyle.values().forEach {
                it.getBackground(requireContext())
                withMain {
                    adapter.data.add(it)
                    adapter.notifyItemInserted(adapter.data.size - 1)
                }
            }
            withMain {
                page_style_list.scrollToPosition(globalConfig.readerPageStyle.value!!)
            }
        }
    }

    class Adapter(val fragment: BookReaderSettingFragment) :
        BaseAdapter<PageStyle>(R.layout.reader_item_page_style) {

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder.itemView.apply {
                val pageStyle = data[position]
                val background = pageStyle.getBackground(context)
                image.setImageDrawable(background)

                if (globalConfig.readerPageStyle.value != position) {
                    image.borderColor = R.color.dn_text_color_black_disable.color(context)
                } else {
                    image.borderColor = R.color.dn_color_primary.color(context)
                }
                setOnClickListener {
                    notifyDataSetChanged()
                    globalConfig.readerPageStyle.postValue(pageStyle.ordinal)
                    //记录浅色 深色样式 和深色样式
                    if (pageStyle.isDark && globalConfig.appDayNightMode != AppCompatDelegate.MODE_NIGHT_NO) {
                        globalConfig.lastDarkTheme.postValue(pageStyle.ordinal)
                    } else if (!pageStyle.isDark && globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_NO) {
                        globalConfig.lastLightTheme.postValue(pageStyle.ordinal)
                    }
                }
            }
        }
    }

}