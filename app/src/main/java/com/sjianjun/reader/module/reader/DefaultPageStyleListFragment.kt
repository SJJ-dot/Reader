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
import kotlinx.android.synthetic.main.item_default_style.view.imageView
import kotlinx.android.synthetic.main.item_default_style.view.textView_content
import kotlinx.android.synthetic.main.item_default_style.view.textView_title
import kotlinx.android.synthetic.main.reader_fragment_default_page_style.btn_cancel
import kotlinx.android.synthetic.main.reader_fragment_default_page_style.btn_save
import kotlinx.android.synthetic.main.reader_fragment_default_page_style.recycler_view
import sjj.novel.view.reader.page.CustomPageStyle
import sjj.novel.view.reader.page.PageStyle

class DefaultPageStyleListFragment : DialogFragment() {
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
        val params = dialog?.window?.attributes
        params?.width = ViewGroup.LayoutParams.MATCH_PARENT
        params?.height = ViewGroup.LayoutParams.MATCH_PARENT
        dialog?.window?.attributes = params

        recycler_view.adapter = adapter
        adapter.data.addAll(PageStyle.builtin().map { CustomPageStyle(it) })

        btn_cancel.setOnClickListener {
            dismiss()
        }
        btn_save.setOnClickListener {
            val item = adapter.data[recycler_view.currentItem]
            CustomPageStyleFragment.newInstance(item.info).show(parentFragmentManager, "CustomPageStyleFragment")
            dismiss()
        }

    }

    class Adapter : BaseAdapter<CustomPageStyle>(R.layout.item_default_style) {
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val style = data[position]
            holder.itemView.imageView.setImageDrawable(style.getBackground(holder.itemView.context))
            holder.itemView.textView_title.setTextColor(style.info.chapterTitleColor)
            holder.itemView.textView_content.setTextColor(style.info.chapterContentColor)
        }
    }
}