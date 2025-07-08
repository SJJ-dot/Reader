package com.sjianjun.reader.module.shelf

import androidx.lifecycle.ViewModel
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.http.WebViewClient
import com.sjianjun.reader.repository.DbFactory.db
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.toast
import io.legado.app.help.http.BackstageWebView.WebViewResponse
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import sjj.alog.Log

class BookShelfViewModel : ViewModel() {

}