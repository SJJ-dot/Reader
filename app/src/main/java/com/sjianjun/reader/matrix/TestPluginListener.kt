package com.sjianjun.reader.matrix

import android.content.Context
import com.tencent.matrix.plugin.DefaultPluginListener
import com.tencent.matrix.report.Issue
import sjj.alog.Log

class TestPluginListener(context: Context?) : DefaultPluginListener(context) {
    override fun onReportIssue(issue: Issue) {
        super.onReportIssue(issue)
        Log.e(issue.toString())

        //add your code to process data
    }
}