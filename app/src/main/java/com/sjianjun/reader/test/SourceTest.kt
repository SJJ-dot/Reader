package com.sjianjun.reader.test

import com.sjianjun.reader.bean.BookSource
import kotlinx.coroutines.delay
import sjj.alog.Log

// val url = "https://www.69shuba.com/modules/article/search.php"
//            val cookie = CookieMgr.getCookie(url,"cf_clearance")
////
object SourceTest {
    suspend fun test() {
        val source = BookSource()
        source.lauanage = BookSource.Language.py
        source.js = """
from SessionManager import get_cookie,start_verification_activity

import requests
from bs4 import BeautifulSoup
from log import log
import os
from os.path import join


def search(query):
    url = "https://www.69shuba.com/modules/article/search.php"
    
    cookie = get_cookie(url)
    if "cf_clearance" not in cookie:
        start_verification_activity(url)
        cookie = get_cookie(url)
    log("Cookie11: " + cookie)
    
    # 将 Cookie 添加到 headers 中
    headers = {
        "Cookie": cookie,
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
    # POST请求的数据
    data = {'searchkey': query.encode('gbk'),'searchtype':'all'}
    # 发送POST请求
    response = requests.post(url, data=data, headers=headers)
    response.encoding = 'gbk'
    log(response.text)
    
    return []

        """.trimIndent()
        delay(5000)
        Log.i("=================")
        source.search("金刚")

    }
}