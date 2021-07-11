package com.sjianjun.reader.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.act
import com.sjianjun.reader.utils.color

/*
 * Created by shen jian jun on 2020-07-08
 */
class DayNightMask @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), LifecycleObserver {

    init {
        post { act?.lifecycle?.addObserver(this) }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        if (globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            setBackgroundColor("#88000000".color)
        } else {
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

}