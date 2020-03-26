package com.sjianjun.reader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sjianjun.reader.http.HttpClient
import com.sjianjun.reader.http.HttpInterface
import com.sjianjun.reader.http.createRetrofit
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sjj.alog.Log

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        GlobalScope.launch {

            val resp = HttpClient().getSync<Resp>("https://log.kxxsc.com/yd/exceptionlog/report")

            assert(resp is Resp)

            Log.e(resp)
        }


    }

    data class Resp(var result: Result? = null)

    data class Result(var status: Status? = null)

    data class Status(var code: Int = 0, var msg: String? = null)
}
