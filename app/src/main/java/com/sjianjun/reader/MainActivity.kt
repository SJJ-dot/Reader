package com.sjianjun.reader

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.sjianjun.reader.http.HttpInterface
import com.sjianjun.reader.http.createRetrofit
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mozilla.javascript.Context

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        GlobalScope.launch {
            createRetrofit().create(HttpInterface::class.java).getRequest("")
        }
    }
}
