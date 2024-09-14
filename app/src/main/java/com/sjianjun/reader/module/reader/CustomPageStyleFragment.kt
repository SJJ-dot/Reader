package com.sjianjun.reader.module.reader

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import com.coorchice.library.SuperTextView
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.toast
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.btn_cancel
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.btn_save
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_background_color
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_background_color_preview
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_background_img_selector
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_content_color
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_content_color_preview
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_label_color
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_label_color_preview
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_title_color
import kotlinx.android.synthetic.main.reader_fragment_custom_page_style.chapter_title_color_preview
import sjj.alog.Log
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.CustomPageStyleInfo
import sjj.novel.view.reader.page.PageStyle


class CustomPageStyleFragment : BottomSheetDialogFragment() {
    private val PICK_IMAGE_REQUEST = 1234
    private val customPageStyleInfo: CustomPageStyleInfo by lazy {
        gson.fromJson(
            arguments?.getString(KEY_CUSTOM_PAGE_STYLE_INFO),
            CustomPageStyleInfo::class.java
        )
    }
    private val pageStyle: CustomPageStyle by lazy {
        CustomPageStyle(customPageStyleInfo)
    }

    override fun getTheme(): Int {
        return R.style.reader_page_style
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.reader_fragment_custom_page_style, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCancelable(false)
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.window?.findViewById<View>(R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        initView()
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        val info = customPageStyleInfo
        EventBus.post(EventKey.CUSTOM_PAGE_STYLE, pageStyle)
        setColor(chapter_title_color, chapter_title_color_preview, info.chapterTitleColor)
        chapter_title_color.setOnClickListener {
            Log.i("info: $info")
            ColorPickerDialogBuilder
                .with(context)
                .setTitle("选择标题色")
                .initialColor(info.chapterTitleColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .density(12)
                .setPositiveButton("确定") { dialog, selectedColor, allColors ->
                    Log.i("onPositiveButton: 0x" + Integer.toHexString(selectedColor))
                    setColor(chapter_title_color, chapter_title_color_preview, selectedColor)
                    info.chapterTitleColor = selectedColor
                    EventBus.post(EventKey.CUSTOM_PAGE_STYLE, pageStyle)
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

        setColor(chapter_content_color, chapter_content_color_preview, info.chapterContentColor)
        chapter_content_color.setOnClickListener {
            Log.i("info: $info")
            ColorPickerDialogBuilder
                .with(context)
                .setTitle("选择正文色")
                .initialColor(info.chapterContentColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .density(12)
                .setPositiveButton("确定") { dialog, selectedColor, allColors ->
                    Log.i("onPositiveButton: 0x" + Integer.toHexString(selectedColor))
                    setColor(chapter_content_color, chapter_content_color_preview, selectedColor)
                    info.chapterContentColor = selectedColor
                    dialog.dismiss()
                    EventBus.post(EventKey.CUSTOM_PAGE_STYLE, pageStyle)
                }
                .setNegativeButton("取消") { dialog, which ->
                    Log.i("取消")
                }
                .build()
                .show()
        }
        chapter_content_color_preview.setOnClickListener {
            chapter_content_color.performClick()
        }

        setColor(chapter_label_color, chapter_label_color_preview, info.labelColor)
        chapter_label_color.setOnClickListener {
            Log.i("info: $info")
            ColorPickerDialogBuilder
                .with(context)
                .setTitle("选择标签色")
                .initialColor(info.labelColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .density(12)
                .setPositiveButton("确定") { dialog, selectedColor, allColors ->
                    Log.i("onPositiveButton: 0x" + Integer.toHexString(selectedColor))
                    setColor(chapter_label_color, chapter_label_color_preview, selectedColor)
                    info.labelColor = selectedColor
                    dialog.dismiss()
                    EventBus.post(EventKey.CUSTOM_PAGE_STYLE, pageStyle)
                }
                .setNegativeButton("取消") { dialog, which ->
                    Log.i("取消")
                }
                .build()
                .show()
        }
        chapter_label_color_preview.setOnClickListener {
            chapter_label_color.performClick()
        }

        setColor(chapter_background_color, chapter_background_color_preview, info.backgroundColor)
        chapter_background_color.setOnClickListener {
            Log.i("info: $info")
            ColorPickerDialogBuilder
                .with(context)
                .setTitle("选择背景色")
                .initialColor(info.backgroundColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .density(12)
                .setPositiveButton("确定") { dialog, selectedColor, allColors ->
                    Log.i("onPositiveButton: 0x" + Integer.toHexString(selectedColor))
                    setColor(
                        chapter_background_color,
                        chapter_background_color_preview,
                        selectedColor
                    )
                    info.backgroundColor = selectedColor
                    info.backgroundImage = ""
                    chapter_background_img_selector.setImageDrawable(
                        pageStyle.getBackground(requireContext(), 36.dp2Px, 36.dp2Px)
                    )
                    dialog.dismiss()
                    EventBus.post(EventKey.CUSTOM_PAGE_STYLE, pageStyle)
                }
                .setNegativeButton("取消") { dialog, which ->
                    Log.i("取消")
                }
                .build()
                .show()
        }
        chapter_background_color_preview.setOnClickListener {
            chapter_background_color.performClick()
        }
        chapter_background_img_selector.setImageDrawable(
            pageStyle.getBackground(requireContext(), 36.dp2Px, 36.dp2Px)
        )
        chapter_background_img_selector.setOnClickListener {
            //打开图片选择器
            // 创建一个意图，用于选择图片
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            // 设置意图的类型为图像类型
            intent.type = "image/*"
            // 启动图片选择器，并等待结果
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }
        btn_save.setOnClickListener {
            val list = globalConfig.customPageStyleInfoList.value!!.toMutableList()
            val index = list.indexOfFirst { it.ordinal == info.ordinal }
            if (index == -1) {
                list.add(info)
            } else {
                list[index] = info
            }
            globalConfig.customPageStyleInfoList.setValue(list)
            globalConfig.readerPageStyle.setValue(pageStyle.ordinal)
            dismiss()
        }
        btn_cancel.setOnClickListener {
            EventBus.post(EventKey.CUSTOM_PAGE_STYLE_CANCEL, pageStyle)
            globalConfig.readerPageStyle.setValue(globalConfig.readerPageStyle.value!!)
            dismiss()
        }
    }

    private fun setColor(textView: SuperTextView, imageView: CircleImageView, color: Int) {
        textView.text = "#" + Integer.toHexString(color)
        textView.setTextColor(color)
        imageView.setImageDrawable(ColorDrawable(color))
    }

    // 在 Activity 中处理图片选择结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            // 获取选择的图片的 URI
            val imageUri = data.data
            //复制到本地
            imageUri?.let { context?.contentResolver?.openInputStream(it) }?.use { inputStream ->
                val localPath =
                    context?.filesDir?.absolutePath + "/styles/custom_page_style_${customPageStyleInfo.ordinal}.png"
                val file = java.io.File(localPath)
                if (file.exists()) {
                    file.delete()
                }
                file.parentFile.mkdirs()
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    customPageStyleInfo.backgroundImage = localPath
                    customPageStyleInfo.backgroundColor = Color.WHITE
                    pageStyle.clearCache()
                }
                chapter_background_img_selector.setImageDrawable(
                    pageStyle.getBackground(
                        requireContext(),
                        36.dp2Px,
                        36.dp2Px
                    )
                )
                EventBus.post(EventKey.CUSTOM_PAGE_STYLE, pageStyle)
            }

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