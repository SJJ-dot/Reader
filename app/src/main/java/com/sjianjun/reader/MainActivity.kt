package com.sjianjun.reader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sjianjun.reader.http.client
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sjj.alog.Log

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        GlobalScope.launch {

            val resp = client.get<String>("https://www.biquge5200.cc/95_95192/")

            Log.e(resp)
        }


    }

    data class Resp(var result: Result? = null)

    data class Result(var status: Status? = null)

    data class Status(var code: Int = 0, var msg: String? = null)
}
