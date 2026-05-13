package com.sjianjun.reader.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
        return new String(responseBytes, StandardCharsets.UTF_8);
    }

    private boolean isEmpty(CharSequence sequence) {
        return sequence != null && sequence.length() != 0;
    }
}
