package com.sjianjun.reader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sjianjun.reader.bean.ATest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import sjj.alog.Log

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        GlobalScope.launch {
            try {

            } catch (e: Throwable) {
                Log.e(e,e)
            }

        }
    }



}
