package com.sjianjun.reader.utils

import android.util.Base64
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.OSSLog
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.sjianjun.reader.App
import com.sjianjun.reader.BuildConfig
import java.nio.charset.StandardCharsets


object OssUtil {


    fun getOSSClient(): OSSClient {
        val endpoint = "http://oss-cn-hangzhou.aliyuncs.com"
//// 填写STS应用服务器地址。
//        String stsServer = "https://example.com"
//// 推荐使用OSSAuthCredentialsProvider。token过期可以及时更新。
//        OSSCredentialProvider credentialProvider = new OSSAuthCredentialsProvider(stsServer);
        OSSLog.enableLog()
// 配置类如果不设置，会有默认配置。
        val conf = ClientConfiguration()
        conf.setConnectionTimeout(15 * 1000); // 连接超时，默认15秒。
        conf.setSocketTimeout(15 * 1000); // socket超时，默认15秒。
        conf.setMaxConcurrentRequest(5); // 最大并发请求数，默认5个。
        conf.setMaxErrorRetry(2); // 失败后最大重试次数，默认2次。
        val k1 = Base64.decode("TFRBSTV0Q3pnVzdIZnVnODIyM0c0dGth", Base64.NO_WRAP).toString(StandardCharsets.UTF_8)
        val k2 = Base64.decode("M2dqUzZXekZ1ZWZCV1RTa3pwRjVSTThlb0dJOElM", Base64.NO_WRAP).toString(StandardCharsets.UTF_8)
        return OSSClient(
            App.app,
            endpoint,
            OSSPlainTextAKSKCredentialProvider(k1, k2)
        )
    }

}