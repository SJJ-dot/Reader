package com.sjianjun.reader.module.reader

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.android.synthetic.main.reader_fragment_setting_view.*
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
    ): View? {
        return inflater.inflate(R.layout.reader_fragment_setting_view, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initBrightness()
        initFontSize()
        initLineSpacing()
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

    fun initLineSpacing() {
        val decimalFormat = DecimalFormat("0.#")
        globalConfig.readerLineSpacing.observe(viewLifecycleOwner, Observer {
            line_spacing_text.text = decimalFormat.format(it)
        })
        line_spacing_decrease.setOnClickListener {
            globalConfig.readerLineSpacing.postValue(decimalFormat.format(globalConfig.readerLineSpacing.value!! - 0.1f).toFloat())
        }
        line_spacing_increase.setOnClickListener {
            globalConfig.readerLineSpacing.postValue( decimalFormat.format(globalConfig.readerLineSpacing.value!! + 0.1f).toFloat())
        }
    }

}