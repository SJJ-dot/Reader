package com.sjianjun.test.legado

import com.sjianjun.test.http.http
import com.sjianjun.test.utils.FileCaches


object Loader {
    suspend fun simpleSource(url: String = "https://raw.iqiq.io/XIU2/Yuedu/master/shuyuan"): String {
        val source = http.get(url).body
//        val newJson = JsonArray()
//        val jsonArray = gson.fromJson<JsonArray>(source)!!
//        for (i in 0 until jsonArray.size()) {
//            val jsonObject = jsonArray.get(i).asJsonObject
//            val newObj = JsonObject()
//            newObj.add("bookSourceName", jsonObject.get("bookSourceName"))
//            newObj.add("bookSourceUrl", jsonObject.get("bookSourceUrl"))
//            newObj.add("ruleBookInfo", jsonObject.get("ruleBookInfo"))
//            newObj.add("ruleContent", jsonObject.get("ruleContent"))
//            newObj.add("ruleSearch", jsonObject.get("ruleSearch"))
//            newObj.add("ruleToc", jsonObject.get("ruleToc"))
//            newObj.add("searchUrl", jsonObject.get("searchUrl"))
//            newJson.add(newObj)
//        }
        return source
    }

}

suspend fun main(args: Array<String>) {
    val rule = Loader.simpleSource()
    FileCaches.save("aa.json",rule.toString())
    println(rule)
}