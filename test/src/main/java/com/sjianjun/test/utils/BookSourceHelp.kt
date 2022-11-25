package com.sjianjun.test.utils

import com.google.gson.JsonObject
import java.io.File

fun main(args: Array<String>) {
    val text = File("BookSource/default.json").readText()
    val jsonObject = gson.fromJson<JsonObject>(text)!!
    val jsonArray = jsonObject.getAsJsonArray("bookSource")
    val names = mutableMapOf<String, JsonObject>()
    jsonArray.forEach {
        val obj = it.asJsonObject
        val name = obj.get("source").asString
        names[name] = obj
    }

    File("BookSource/js/").listFiles().forEach {
        val content = it.readText()
        val source = it.nameWithoutExtension
        val obj = names[source]
        if (obj != null) {
            if (obj.get("js").asString != content) {
                obj.addProperty("js", content)
                obj.addProperty("version", obj.get("version").asInt + 1)
            }
        } else {
            val nObj = JsonObject()
            nObj.addProperty("source",source)
            nObj.addProperty("js",content)
            nObj.addProperty("version",1)
            nObj.addProperty("original",false)
            nObj.addProperty("enable",true)
            nObj.addProperty("requestDelay",-1)
            nObj.addProperty("website","")
            names[source] = nObj
            jsonArray.add(nObj)
        }
    }
    File("BookSource/default.json").writeText(gson.toJson(jsonObject))
}