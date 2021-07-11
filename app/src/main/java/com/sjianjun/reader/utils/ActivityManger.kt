package com.sjianjun.reader.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.*

/*
 * Created by shen jian jun on 2020-07-20
 */
object ActivityManger {

    val activityList = LinkedList<Activity>()

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(callback)
    }

    inline fun <reified T> finishAll() {
        activityList.forEach {
            if (it is T) {
                it.finish()
            }
        }
    }

    fun finishSameType(activity: Activity) {
        val target = activity::class.java
        activityList.forEach {
            if (it::class.java == target && it != activity) {
                it.finish()
            }
        }
    }

    private val callback = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPaused(activity: Activity) {

        }

        override fun onActivityStarted(activity: Activity) {

        }

        override fun onActivityDestroyed(activity: Activity) {
            activityList.remove(activity)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

        }

        override fun onActivityStopped(activity: Activity) {

        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activityList.add(activity)
        }

        override fun onActivityResumed(activity: Activity) {

        }
    }


}