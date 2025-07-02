package com.sjianjun.reader.module.reader

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.databinding.ItemDefaultStyleBinding
import com.sjianjun.reader.databinding.ReaderFragmentDefaultPageStyleBinding
import com.sjianjun.reader.view.click
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.PageStyle

class DefaultPageStyleListFragment : DialogFragment() {
    var binding: ReaderFragmentDefaultPageStyleBinding? = null
    private val adapter = Adapter()
    override fun getTheme(): Int {
        return R.style.reader_setting_dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.reader_fragment_default_page_style, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.window?.findViewById<View>(R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        isCancelable = false
        binding = ReaderFragmentDefaultPageStyleBinding.bind(view)
        val params = dialog?.window?.attributes
        params?.width = ViewGroup.LayoutParams.MATCH_PARENT
        params?.height = ViewGroup.LayoutParams.MATCH_PARENT
        dialog?.window?.attributes = params

        binding?.recyclerView!!.adapter = adapter
        adapter.data.addAll(PageStyle.builtin().map { CustomPageStyle(it) })

        binding?.btnCancel!!.click {
            dismiss()
        }
        binding?.btnSave!!.click {
            val item = adapter.data[binding?.recyclerView!!.currentItem]
            CustomPageStyleFragment.newInstance(item.info).show(parentFragmentManager, "CustomPageStyleFragment")
            dismiss()
        }

    }

    class Adapter : BaseAdapter<CustomPageStyle>(R.layout.item_default_style) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = ItemDefaultStyleBinding.bind(holder.itemView)
            val style = data[position]
            binding.imageView.setImageDrawable(style.getBackground(holder.itemView.context))
            binding.textViewTitle.setTextColor(style.info.chapterTitleColor)
            binding.textViewContent.setTextColor(style.info.chapterContentColor)
        }
    }
}