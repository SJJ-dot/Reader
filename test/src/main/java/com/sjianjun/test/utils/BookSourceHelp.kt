package com.sjianjun.test.utils

import com.google.gson.JsonObject
import java.io.File
import java.util.*
import java.nio.charset.Charset

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType

/**
     * 汉字转为拼音
     * @param chinese
     * @return
     */
fun toPinyin(chinese: String): String {
    var pinyinStr = ""
    val newChar = chinese.toCharArray()
    val defaultFormat = HanyuPinyinOutputFormat()
    defaultFormat.caseType = HanyuPinyinCaseType.LOWERCASE
    defaultFormat.toneType = HanyuPinyinToneType.WITHOUT_TONE
    for (i in newChar.indices) {
        if (newChar[i].code > 128) {
            pinyinStr += PinyinHelper.toHanyuPinyinStringArray(newChar[i], defaultFormat)[0]
        } else {
            pinyinStr += newChar[i]
        }
    }
    return pinyinStr
}

fun main(args: Array<String>) {
    val text = File("BookSource/default.json").readText()
    val jsonObject = gson.fromJson<JsonObject>(text)!!
    val jsonArray = jsonObject.getAsJsonArray("bookSource")
    val names = mutableMapOf<String, JsonObject>()
    jsonArray.forEach {
        val obj = it.asJsonObject
        val name = obj.get("source").asString
        val js = obj.get("js").asString

        names[toPinyin(name)] = obj
        // println("BookSource/js/${toPinyin(name)}.js")
        // File("BookSource/js/${toPinyin(name)}.js").writeText(js)
    }

    File("BookSource/js/").listFiles().forEach {
        val content = it.readText()
        val source = it.nameWithoutExtension
        val obj = names[toPinyin(source)]
        if (obj != null) {
            if (obj.get("js").asString.replace("\r\n","\n") != content.replace("\r\n","\n")) {
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
    val json = gson.toJson(jsonObject)
    val gz = Base64.getEncoder().encodeToString(json.zip())
    File("BookSource/default.json").writeText(json)
    File("BookSource/default.json.gzip").writeText(gz)

}