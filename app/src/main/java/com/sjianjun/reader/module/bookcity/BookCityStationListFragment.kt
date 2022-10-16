package com.sjianjun.reader.module.bookcity

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.coroutine.launchIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookSourceManager
import com.sjianjun.reader.utils.color
import kotlinx.android.synthetic.main.bookcity_fragment_station_list.*
import kotlinx.android.synthetic.main.bookcity_item_station.view.*

class BookCityStationListFragment : BaseAsyncFragment() {
    val adapter = Adapter()
    override fun getLayoutRes() = R.layout.bookcity_fragment_station_list

    override val onLoadedView: (View) -> Unit = {
        bookcity_station_list.adapter = adapter
        initData()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initData() {
        launchIo(singleCoroutineKey = "initBookCityStationList") {
            val javaScriptList = BookSourceManager.getAllJs().filter {
                it.enable && it.website.isNotBlank()
            }
            withMain {
                adapter.data.clear()
                adapter.data.addAll(javaScriptList)
                adapter.notifyDataSetChanged()
            }
        }
    }


    class Adapter : BaseAdapter<BookSource>(R.layout.bookcity_item_station) {

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val javaScript = data[position]
            holder.itemView.apply {
                title.text = javaScript.source
                if (globalConfig.bookCityDefaultSource.value == javaScript.source) {
                    title.setTextColor(R.color.dn_color_primary.color(context))
                } else {
                    title.setTextColor(R.color.dn_text_color_black.color(context))
                }
                setOnClickListener {
                    notifyDataSetChanged()
                    globalConfig.bookCityDefaultSource.postValue(javaScript.source)
                }
            }
        }
    }
}