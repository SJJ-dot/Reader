package com.sjianjun.reader.test

import com.sjianjun.reader.bean.BookSource

object SourceTest {
    suspend fun test() {
        val source = BookSource()
        source.lauanage = BookSource.Language.py
        source.js = """
            from SessionManager import SessionManager

            import requests
            from bs4 import BeautifulSoup
            from log import log
            import os
            from os.path import join
            

            def search(query):
                log(query)

                return []

        """.trimIndent()
        source.search("金刚")
    }
}