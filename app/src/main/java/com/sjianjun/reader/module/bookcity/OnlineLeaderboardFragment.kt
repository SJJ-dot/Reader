package com.sjianjun.reader.module.bookcity

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.databinding.FragmentOnlineLeaderboardBinding
import com.sjianjun.reader.databinding.ItemOnlineLeaderboardBinding
import com.sjianjun.reader.mqtt.OnlineInfos
import com.sjianjun.reader.mqtt.RankingInfo
import com.sjianjun.reader.mqtt.formatDuration
import com.sjianjun.reader.mqtt.user
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import kotlinx.coroutines.launch
import sjj.alog.Log

class OnlineLeaderboardFragment : Fragment() {
    private var binding: FragmentOnlineLeaderboardBinding? = null
    private val adapter = RankingListAdapter()
    private var currentPeriod = "day"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_online_leaderboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentOnlineLeaderboardBinding.bind(view)
        binding?.apply {
            rvLeaderboard.layoutManager = LinearLayoutManager(requireContext())
            rvLeaderboard.adapter = adapter

            // 周期选择
            tabPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    currentPeriod = when (tab?.position) {
                        0 -> "day"
                        1 -> "week"
                        2 -> "month"
                        3 -> "year"
                        4 -> "total"
                        else -> "day"
                    }
                    loadLeaderboard()
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            // 初始加载
            loadLeaderboard()
        }
    }

    private fun loadLeaderboard() {
        lifecycleScope.launch {
            val leaderboard = OnlineInfos.getOnlineLeaderboard(currentPeriod)
            if (leaderboard?.items != null) {
                adapter.data = leaderboard.items!!.toMutableList()
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private class RankingListAdapter : BaseAdapter<RankingInfo>(R.layout.item_online_leaderboard) {

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val binding = ItemOnlineLeaderboardBinding.bind(holder.itemView)
            val item = data[position]

            binding.tvRank.text = item.rank.toString()
            binding.tvUser.text = item.user_id.user
            binding.tvLevel.text = item.level?.levelName ?: "--"
            binding.tvOnlineTime.text = item.online_seconds.formatDuration()
            val context = holder.itemView.context
            if (item.user_id == globalConfig.mqttClientId) {
                binding.root.setCardBackgroundColor(R.color.dn_color_primary.color(context))
                binding.tvUser.setTextColor(Color.WHITE)
                binding.tvLevel.setTextColor(Color.WHITE)
                binding.tvOnlineTime.setTextColor(Color.WHITE)
            } else {
                binding.root.setCardBackgroundColor(R.color.dn_background_card.color(context))
                binding.tvUser.setTextColor(R.color.mdr_grey_500.color(context))
                binding.tvLevel.setTextColor(R.color.colorPrimary.color(context))
                binding.tvOnlineTime.setTextColor(R.color.dn_text_color_black.color(context))
            }
        }

    }

    companion object {
        fun newInstance() = OnlineLeaderboardFragment()
    }
}

