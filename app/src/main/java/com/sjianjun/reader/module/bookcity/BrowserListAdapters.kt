package com.sjianjun.reader.module.bookcity

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.databinding.FragmentBookCityPageHostItemBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHistoryItemBinding
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.setTextColorRes
import com.sjianjun.reader.view.click
import java.text.SimpleDateFormat
import java.util.Locale

/** 把拦截范围 mode 转成中文标签 */
private fun modeLabel(mode: String): String = when (mode) {
    BLOCK_MODE_DOMAIN -> "顶级域名"
    BLOCK_MODE_SUBDOMAIN -> "子域名"
    BLOCK_MODE_URL -> "精确URL"
    else -> "顶级域名"
}

class BlackListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
        val host = data[position]
        if (host.isPage) {
            binding.root.setCardBackgroundColor(R.color.colorPrimary.color(holder.itemView.context))
            binding.tvHost.setTextColorRes(R.color.mdr_white)
            binding.tvType.setTextColorRes(R.color.mdr_white)
            binding.tvTime.setTextColorRes(R.color.mdr_white)
            binding.btnMarkBlack.setTextColorRes(R.color.colorPrimary)
            binding.btnMarkBlack.setSolid(R.color.mdr_white.color(holder.itemView.context))
        } else {
            binding.root.setCardBackgroundColor(R.color.dn_bookcity_host_item_background.color(holder.itemView.context))
            binding.tvHost.setTextColorRes(R.color.dn_text_color_black)
            binding.tvType.setTextColorRes(R.color.colorPrimary)
            binding.tvTime.setTextColorRes(R.color.dn_text_color_black)
            binding.btnMarkBlack.setTextColorRes(R.color.mdr_white)
            binding.btnMarkBlack.setSolid(R.color.colorPrimary.color(holder.itemView.context))
        }
        binding.tvType.text = "${modeLabel(host.mode)} · 命中 ${adBlock.getHitCount(host.host, host.mode)} 次"
        binding.tvHost.text = host.host
        binding.tvTime.text = host.time
        binding.btnMarkBlack.text = "-移除"
        binding.btnMarkBlack.click {
            adBlock.removeBlackHost(data[position])
        }
    }
}

class WhiteListAdapter(var adBlock: AdBlock) : BaseAdapter<HostStr>(R.layout.fragment_book_city_page_host_item) {
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val binding = FragmentBookCityPageHostItemBinding.bind(holder.itemView)
        val host = data[position]
        if (host.isPage) {
            binding.root.setCardBackgroundColor(R.color.colorPrimary.color(holder.itemView.context))
            binding.tvHost.setTextColorRes(R.color.mdr_white)
            binding.tvType.setTextColorRes(R.color.mdr_white)
            binding.tvTime.setTextColorRes(R.color.mdr_white)
            binding.btnMarkBlack.setTextColorRes(R.color.colorPrimary)
            binding.btnMarkBlack.setSolid(R.color.mdr_white.color(holder.itemView.context))
        } else {
            binding.root.setCardBackgroundColor(R.color.dn_bookcity_host_item_background.color(holder.itemView.context))
            binding.tvHost.setTextColorRes(R.color.dn_text_color_black)
            binding.tvType.setTextColorRes(R.color.colorPrimary)
            binding.tvTime.setTextColorRes(R.color.dn_text_color_black)
            binding.btnMarkBlack.setTextColorRes(R.color.mdr_white)
            binding.btnMarkBlack.setSolid(R.color.colorPrimary.color(holder.itemView.context))
        }
        binding.tvType.text = "${modeLabel(host.mode)} · 命中 ${adBlock.getHitCount(host.host, host.mode)} 次"
        binding.tvHost.text = host.host
        binding.tvTime.text = host.time
        binding.btnMarkBlack.text = "-移除"
        binding.btnMarkBlack.click {
            adBlock.removeWhiteHost(data[position])
        }
    }
}

class HistoryListAdapter : BaseAdapter<HistoryItem>(R.layout.fragment_book_city_page_history_item) {
    var onClick: ((url: String) -> Unit)? = null
    var onDeleteHistory: ((item: HistoryItem?) -> Unit)? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val binding = FragmentBookCityPageHistoryItemBinding.bind(holder.itemView)
        val history = data[position]
        binding.tvTitle.text = history.title
        binding.tvTime.text = sdf.format(history.time)
        binding.root.click {
            onClick?.invoke(history.url)
        }
        binding.root.setOnLongClickListener {
            MaterialAlertDialogBuilder(it.context).apply {
                setTitle("删除记录")
                setMessage("是否删除 ${history.title} ?")
                setNegativeButton("取消", null)
                setPositiveButton("删除") { _, _ ->
                    onDeleteHistory?.invoke(history)
                }
                setNeutralButton("清空") { _, _ ->
                    MaterialAlertDialogBuilder(it.context).apply {
                        setTitle("清空全部记录")
                        setMessage("是否清空全部历史记录 ?")
                        setNegativeButton("取消", null)
                        setPositiveButton("清空") { _, _ ->
                            onDeleteHistory?.invoke(null)
                        }.show()
                    }
                }
            }.show()
            return@setOnLongClickListener true
        }
    }
}
