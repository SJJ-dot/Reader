package com.sjianjun.reader.module.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.toast
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_title_color
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_title_color_preview
import sjj.alog.Log
import sjj.novel.view.reader.page.CustomPageStyleInfo


class CustomPageStyleFragment : BottomSheetDialogFragment() {
    private val customPageStyleInfo: CustomPageStyleInfo by lazy {
        gson.fromJson(arguments?.getString(KEY_CUSTOM_PAGE_STYLE_INFO), CustomPageStyleInfo::class.java)
    }

    override fun getTheme(): Int {
        return R.style.reader_page_style
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.reader_fragment_custom_page_style, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.window?.findViewById<View>(R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        initView()
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        val info = customPageStyleInfo
        chapter_title_color_preview.setImageDrawable(ColorDrawable(info.chapterTitleColor))
        chapter_title_color.text = "#" + Integer.toHexString(info.chapterTitleColor)
        chapter_title_color.setTextColor(info.chapterTitleColor)
        chapter_title_color.strokeColor = info.chapterTitleColor
        chapter_title_color.setOnClickListener {
            ColorPickerDialogBuilder
                .with(context)
                .setTitle("选择标题色")
                .initialColor(info.chapterTitleColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .density(12)
                .setPositiveButton("确定") { dialog, selectedColor, allColors ->
                    Log.i("onPositiveButton: 0x" + Integer.toHexString(selectedColor))
                    chapter_title_color_preview.setImageDrawable(ColorDrawable(selectedColor))
                    chapter_title_color.text = "#" + Integer.toHexString(selectedColor)
                    chapter_title_color.setTextColor(selectedColor)
                    chapter_title_color.strokeColor = selectedColor
                    info.chapterTitleColor = selectedColor
                    dialog.dismiss()
                }
                .setNegativeButton("取消") { dialog, which ->
                    Log.i("取消")
                }
                .build()
                .show()
        }
        chapter_title_color_preview.setOnClickListener {
            chapter_title_color.performClick()
        }
    }

    companion object {
        private const val KEY_CUSTOM_PAGE_STYLE_INFO = "customPageStyleInfo"
        fun newInstance(customPageStyleInfo: CustomPageStyleInfo): CustomPageStyleFragment {
            //为什么marsCode 没有提示
            val fragment = CustomPageStyleFragment()
            val bundle = Bundle()
            bundle.putString(KEY_CUSTOM_PAGE_STYLE_INFO, gson.toJson(customPageStyleInfo))
            fragment.arguments = bundle
            return fragment
        }

    }
}