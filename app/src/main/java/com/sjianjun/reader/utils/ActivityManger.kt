package com.sjianjun.reader.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.system.exitProcess

/*
 * Created by shen jian jun on 2020-07-20
 */
object ActivityManger {

    val activityList = ConcurrentLinkedQueue<Activity>()

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(callback)
    }

    fun exitApp() {
        val activity = activityList.lastOrNull() ?: return
        activity.runOnUiThread {
            val snapshot = activityList.toList()
            snapshot.forEach {
                if (!it.isFinishing && !it.isDestroyed) {
                    it.finish()
                }
            }
            activity.finishAffinity()
            activity.finishAndRemoveTask()
        }
    }

    fun finishSameType(activity: Activity) {
        val target = activity::class.java
        activityList.toList().forEach {
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