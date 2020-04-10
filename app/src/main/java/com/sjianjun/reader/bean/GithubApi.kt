package com.sjianjun.reader.bean

class GithubApi {
    public var tag_name: String = ""
    public var name: String = ""
    public var body: String = ""
    public var assets: List<Assets>? = null

    class Assets {

        public var browser_download_url = ""
        public var content_type = ""
        public var download_count = ""
    }
}