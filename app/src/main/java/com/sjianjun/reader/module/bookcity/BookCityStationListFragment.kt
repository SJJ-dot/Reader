package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.coroutine.launch
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
        launch(singleCoroutineKey = "initBookCityStationList") {
            val javaScriptList = DataManager.getAllJavaScript().first().filter { it.enable }
            adapter.data.clear()
            adapter.data.addAll(javaScriptList)
            adapter.notifyDataSetChanged()
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