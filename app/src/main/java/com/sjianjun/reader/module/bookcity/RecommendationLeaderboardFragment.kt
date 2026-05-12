package com.sjianjun.reader.module.bookcity

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.databinding.FragmentRecommendationLeaderboardBinding
import com.sjianjun.reader.databinding.ItemRecommendationLeaderboardBinding
import com.sjianjun.reader.module.reader.activity.BrowserReaderActivity
import com.sjianjun.reader.mqtt.Recommendation
import com.sjianjun.reader.mqtt.Recommendations
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.launch

class RecommendationLeaderboardFragment : Fragment() {
    private var binding: FragmentRecommendationLeaderboardBinding? = null
    private val adapter = RecommendationListAdapter()
    private var currentPeriod = "day"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recommendation_leaderboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentRecommendationLeaderboardBinding.bind(view)
        binding?.apply {
            rvLeaderboard.layoutManager = LinearLayoutManager(requireContext())
            rvLeaderboard.adapter = adapter

            tabPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    currentPeriod = when (tab?.position) {
                        0 -> "day"
                        1 -> "week"
                        2 -> "month"
                        3 -> "total"
                        else -> "day"
                    }
                    loadLeaderboard()
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {
                }

                override fun onTabReselected(tab: TabLayout.Tab?) {
                }
            })

            loadLeaderboard()
        }
    }

    private fun loadLeaderboard() {
        lifecycleScope.launch {
            val list = Recommendations.getRecommendations(currentPeriod, limit = 250, offset = 0)
            adapter.data = (list ?: emptyList()).toMutableList()
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private inner class RecommendationListAdapter : BaseAdapter<Recommendation>(R.layout.item_recommendation_leaderboard) {
        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = ItemRecommendationLeaderboardBinding.bind(holder.itemView)
            val item = data[position]
            val context = holder.itemView.context
            binding.tvRank.text = (position + 1).toString()
            binding.tvTitle.text = item.book_title.ifBlank { "--" }
            binding.tvAuthor.text = if (item.author.isBlank()) "作者：--" else "作者：${item.author}"
            binding.tvCount.text = "推荐 ${item.recommendation_count}"

            if (position < 3) {
                binding.tvRank.solid = R.color.colorPrimary.color(context)
                binding.tvRank.setTextColor(R.color.mdr_white.color(context))
            } else {
                binding.tvRank.solid = R.color.dn_background_card.color(context)
                binding.tvRank.setTextColor(R.color.dn_text_color_black.color(context))
            }

            // Add click listener for opening book URL
            binding.clickableArea.click {
                if (item.book_url.isNotBlank()) {
                    BrowserReaderActivity.startActivity(context, item.book_url)
                } else {
                    toast("暂无书籍链接")
                }
            }

            if (globalConfig.admin) {
                binding.btnDelete.visibility = View.VISIBLE
                binding.btnDelete.setOnClickListener {
                    if (item.id.isBlank()) {
                        toast("删除失败：记录ID为空")
                        return@setOnClickListener
                    }
                    MaterialAlertDialogBuilder(context)
                        .setTitle("删除推荐")
                        .setMessage("确定要删除这条推荐吗？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除") { _, _ ->
                            lifecycleScope.launch {
                                val ok = Recommendations.deleteRecommendation(item.id)
                                if (ok) {
                                    val removePos = holder.bindingAdapterPosition
                                    if (removePos != RecyclerView.NO_POSITION && removePos < data.size) {
                                        data.removeAt(removePos)
                                        notifyItemRemoved(removePos)
                                        notifyItemRangeChanged(removePos, data.size - removePos)
                                    } else {
                                        loadLeaderboard()
                                    }
                                    toast("删除成功")
                                } else {
                                    toast("删除失败")
                                }
                            }
                        }
                        .show()

                }
            } else {
                binding.btnDelete.visibility = View.GONE
                binding.btnDelete.setOnClickListener(null)
            }
        }
    }

    companion object {
        fun newInstance() = RecommendationLeaderboardFragment()
    }
}

