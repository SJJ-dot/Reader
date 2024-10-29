package com.sjianjun.reader.module.reader

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.coorchice.library.SuperTextView
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ReaderFragmentCustomPageStyleBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.show
import de.hdodenhof.circleimageview.CircleImageView
import sjj.alog.Log
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.CustomPageStyleInfo
import sjj.novel.view.reader.page.PageStyle


class CustomPageStyleFragment : BottomSheetDialogFragment() {
    var binding: ReaderFragmentCustomPageStyleBinding? = null
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
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.window?.findViewById<View>(R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        binding = ReaderFragmentCustomPageStyleBinding.bind(view)
        initView()
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        val info = customPageStyleInfo
        EventBus.post(EventKey.CUSTOM_PAGE_STYLE, pageStyle)
        setColor(binding?.chapterTitleColor!!, binding?.chapterTitleColorPreview!!, info.chapterTitleColor)
        binding?.chapterTitleColor!!.setOnClickListener {
            Log.i("info: $info")
            ColorPickerDialogBuilder
                .with(context)
                .setTitle("选择标题色")
                .initialColor(info.chapterTitleColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .density(12)
                .setPositiveButton("确定") { dialog, selectedColor, allColors ->
                    Log.i("onPositiveButton: 0x" + Integer.toHexString(selectedColor))
                    setColor(binding?.chapterTitleColor!!, binding?.chapterTitleColorPreview!!, selectedColor)
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
        binding?.chapterTitleColorPreview!!.setOnClickListener {
            binding?.chapterTitleColor!!.performClick()
        }

        setColor(binding?.chapterContentColor!!, binding?.chapterContentColorPreview!!, info.chapterContentColor)
        binding?.chapterContentColor!!.setOnClickListener {
            Log.i("info: $info")
            ColorPickerDialogBuilder
                .with(context)
                .setTitle("选择正文色")
                .initialColor(info.chapterContentColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .density(12)
                .setPositiveButton("确定") { dialog, selectedColor, allColors ->
                    Log.i("onPositiveButton: 0x" + Integer.toHexString(selectedColor))
                    setColor(binding?.chapterContentColor!!, binding?.chapterContentColorPreview!!, selectedColor)
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
        binding?.chapterContentColorPreview!!.setOnClickListener {
            binding?.chapterContentColor!!.performClick()
        }

        setColor(binding?.chapterLabelColor!!, binding?.chapterLabelColorPreview!!, info.labelColor)
        binding!!.chapterLabelColor.setOnClickListener {
            Log.i("info: $info")
            ColorPickerDialogBuilder
                .with(context)
                .setTitle("选择标签色")
                .initialColor(info.labelColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .density(12)
                .setPositiveButton("确定") { dialog, selectedColor, allColors ->
                    Log.i("onPositiveButton: 0x" + Integer.toHexString(selectedColor))
                    setColor(binding?.chapterLabelColor!!, binding?.chapterLabelColorPreview!!, selectedColor)
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
        binding?.chapterLabelColorPreview!!.setOnClickListener {
            binding?.chapterLabelColor!!.performClick()
        }


        binding?.chapterBackgroundColor!!.setOnClickListener {
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
                        binding?.chapterBackgroundColor!!,
                        binding?.chapterBackgroundImgSelector!!,
                        selectedColor
                    )
                    info.backgroundColor = selectedColor
                    info.backgroundImage = ""
                    info.backgroundRes = 0
                    pageStyle.clearCache()
                    dialog.dismiss()
                    EventBus.post(EventKey.CUSTOM_PAGE_STYLE, pageStyle)
                }
                .setNegativeButton("取消") { dialog, which ->
                    Log.i("取消")
                }
                .build()
                .show()
        }
        setColor(binding?.chapterBackgroundColor!!, binding?.chapterBackgroundImgSelector!!, info.backgroundColor)
        if (info.backgroundImage.isNotEmpty() || info.backgroundRes != 0) {
            binding?.chapterBackgroundImgSelector!!.setImageDrawable(
                pageStyle.getBackground(requireContext(), 36.dp2Px, 36.dp2Px)
            )
        }

        binding?.chapterBackgroundImgSelector!!.setOnClickListener {
            //打开图片选择器
            // 创建一个意图，用于选择图片
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            // 设置意图的类型为图像类型
            intent.type = "image/*"
            // 启动图片选择器，并等待结果
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        binding?.rgStatusBarColor!!.setOnCheckedChangeListener { group, checkedId ->
            info.isDark = checkedId == R.id.rb_status_bar_color_white
            if (pageStyle.isDark) {
                ImmersionBar.with(this).statusBarDarkFont(false).init()
            } else {
                ImmersionBar.with(this).statusBarDarkFont(true).init()
            }

            EventBus.post(EventKey.CUSTOM_PAGE_STYLE, pageStyle)
        }
        binding?.rgStatusBarColor!!.check(if (info.isDark) R.id.rb_status_bar_color_white else R.id.rb_status_bar_color_black)

        binding?.btnSave!!.setOnClickListener {
            val list = globalConfig.customPageStyleInfoList.value!!.toMutableList()
            val index = list.indexOfFirst { it.id == info.id }
            if (index == -1) {
                list.add(info)
            } else {
                list[index] = info
            }
            globalConfig.customPageStyleInfoList.setValue(list)
            globalConfig.readerPageStyle.postValue(pageStyle.id)
            dismiss()
        }
        binding?.btnCancel!!.setOnClickListener {
            globalConfig.readerPageStyle.postValue(globalConfig.readerPageStyle.value!!)
            dismiss()
        }
        binding?.btnDelete!!.setOnClickListener {
            //弹窗
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除")
                .setMessage("是否删除该自定义样式？")
                .setPositiveButton("确定") { dialog, which ->
                    dialog.dismiss()

                    val list = globalConfig.customPageStyleInfoList.value!!.toMutableList()
                    list.removeAll { it.id == info.id }
                    globalConfig.customPageStyleInfoList.setValue(list)
                    if (customPageStyleInfo.id == globalConfig.readerPageStyle.value!!) {
                        globalConfig.readerPageStyle.postValue(PageStyle.defDay.id)
                    } else {
                        globalConfig.readerPageStyle.postValue(globalConfig.readerPageStyle.value!!)
                    }
                    dismiss()
                }
                .setNegativeButton("取消") { dialog, which ->
                    dialog.dismiss()
                }
                .show()


        }
        if (info.isDeleteable) {
            binding?.btnDelete!!.show()
        } else {
            binding?.btnDelete!!.gone()
        }
    }

    private fun setColor(textView: SuperTextView, imageView: ImageView, color: Int) {
        textView.text = "#" + Integer.toHexString(color)
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
                    context?.filesDir?.absolutePath + "/styles/custom_${customPageStyleInfo.id}.png"
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
                    customPageStyleInfo.backgroundRes = 0
                    pageStyle.clearCache()
                }
                binding?.chapterBackgroundImgSelector!!.setImageDrawable(pageStyle.getBackground(requireContext(), 36.dp2Px, 36.dp2Px))
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