package com.sjianjun.reader.test

import com.sjianjun.reader.bean.BookSource
import kotlinx.coroutines.delay
import sjj.alog.Log

object SourceTest {
    suspend fun test() {
        val source = BookSource()
        source.lauanage = BookSource.Language.py
        source.js = """
from WebViewClient import web_get
from SessionManager import start_verification_activity

import requests
from bs4 import BeautifulSoup
from log import log
import os
from os.path import join
import json


def search(query):
    return []

        """.trimIndent()
        delay(5000)
        Log.i("=================")
        source.search("金刚")

    }
}