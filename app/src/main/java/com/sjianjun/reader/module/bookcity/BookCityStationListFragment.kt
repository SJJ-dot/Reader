package com.sjianjun.reader.module.bookcity

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.coroutine.launchIo
import com.sjianjun.reader.coroutine.withMain
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import kotlinx.android.synthetic.main.bookcity_fragment_station_list.*
import kotlinx.android.synthetic.main.bookcity_item_station.view.*
import kotlinx.coroutines.flow.first

class BookCityStationListFragment : BaseAsyncFragment() {
    val adapter = Adapter()
    override fun getLayoutRes() = R.layout.bookcity_fragment_station_list

    override val onLoadedView: (View) -> Unit = {
        bookcity_station_list.adapter = adapter
        initData()
    }

    private fun initData() {
        launchIo(singleCoroutineKey = "initBookCityStationList") {
            val javaScriptList = DataManager.getAllJavaScript().first()
                .filter { it.enable && !it.execute<String>("hostUrl;").isNullOrBlank() }
            withMain {
                adapter.data.clear()
                adapter.data.addAll(javaScriptList)
                adapter.notifyDataSetChanged()
            }
        }
    }


    class Adapter : BaseAdapter<JavaScript>(R.layout.bookcity_item_station) {

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val javaScript = data[position]
            holder.itemView.apply {
                title.text = javaScript.source
                setOnClickListener {
                    globalConfig.bookCityDefaultSource.postValue(javaScript.source)
                }
            }
        }
    }
}