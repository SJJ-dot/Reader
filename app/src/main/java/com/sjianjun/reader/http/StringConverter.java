package com.sjianjun.reader.http;

import com.sjianjun.charset.CharsetDetector;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public class StringConverter {
    public String stringConverter(ResponseBody value) throws IOException {
        String charsetStr;
        MediaType mediaType = value.contentType();
        BufferedSource source = value.source();
        source.request(Long.MAX_VALUE);
        byte[] responseBytes = source.buffer().readByteArray();

        //根据http头判断
        if (mediaType != null) {
            Charset charset = mediaType.charset();
            if (charset != null) {
                charsetStr = charset.displayName();
                if (isEmpty(charsetStr)) {
                    return new String(responseBytes, Charset.forName(charsetStr));
                }
            }
        }
        //根据meta判断
        byte[] headerBytes = Arrays.copyOfRange(responseBytes, 0, Math.min(responseBytes.length, 1024));
        Document doc = Jsoup.parse(new String(headerBytes, Charset.forName("UTF-8")));
        Elements metaTags = doc.getElementsByTag("meta");
        for (Element metaTag : metaTags) {
            String content = metaTag.attr("content");
            String http_equiv = metaTag.attr("http-equiv");
            charsetStr = metaTag.attr("charset");
            if (!charsetStr.isEmpty()) {
                if (isEmpty(charsetStr)) {
                    return new String(responseBytes, Charset.forName(charsetStr));
                }
            }
            if (http_equiv.toLowerCase().equals("content-type")) {
                if (content.toLowerCase().contains("charset")) {
                    charsetStr = content.substring(content.toLowerCase().indexOf("charset") + "charset=".length());
                } else {
                    charsetStr = content.substring(content.toLowerCase().indexOf(";") + 1);
                }
                if (isEmpty(charsetStr)) {
                    try {
                        return new String(responseBytes, Charset.forName(charsetStr));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        //根据内容判断
        charsetStr = CharsetDetector.detectCharset(new ByteArrayInputStream(responseBytes));
        return new String(responseBytes, Charset.forName(charsetStr));
    }

    private boolean isEmpty(CharSequence sequence) {
        return sequence != null && sequence.length() != 0;
    }
}
