# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

-keep class android.support.**{*;}

-keep class org.mozilla.javascript.** { *; }
# ============================================================
# 应用包安全网（重要）
# 保留整个应用包的类名与成员名。原因：项目包含 Python 书源引擎、
# JS 引擎、WebView @JavascriptInterface、大量自定义 View 与反射调用，
# 一旦混淆类名/字段名会导致运行期崩溃；当前 release 依赖此规则稳定运行。
# 若后续要真正启用混淆（减小体积/防反编译），需逐类排查上述反射点，
# 把下面这条全量 keep 替换为「只 keep 序列化/反射相关类」的精确规则。
# ============================================================
-keep class com.sjianjun.reader.** { *; }

# —— 序列化/持久化类精确 keep（即使上面全量 keep 已覆盖，这里显式声明意图，
#    也方便将来收紧时直接复用）——
# Room 实体 + 所有 Gson 模型：必须保留字段名，否则列名/JSON 字段匹配失败
-keep class com.sjianjun.reader.bean.** { *; }
# 广告拦截旧数据模型（Gson 反序列化 MMKV 旧数据）
-keep class com.sjianjun.reader.module.bookcity.HostStr { *; }
# 浏览器数据迁移用的旧数据结构（Gson）
-keep class com.sjianjun.reader.repository.BrowserDataMigration$* { *; }

-keep class sjj.novel.view.reader.** { *; }
-keep class org.jsoup.Jsoup { *; }
-keep class org.jsoup.** { *; }
-keep class sjj.alog.Log { *; }
-keep class org.jsoup.internal.StringUtil { *; }
-keep class androidx.core.content.FileProvider { *; }

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# for DexGuard only
#-keepresourcexmlelements manifest/application/meta-data@value=GlideModule

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep class com.coorchice.library.gifdecoder.JNI { *; }
-keep class okhttp3.** { *; }
-keep class com.alibaba.sdk.android.** { *; }


-keep class com.umeng.** {*;}

-keep class org.repackage.** {*;}

-keepclassmembers class * {
   public <init> (org.json.JSONObject);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep public class com.sjianjun.reader.R$*{
public static final int *;
}

-keep class com.pgyer.pgyersdk.** { *; }
-keep class com.pgyer.pgyersdk.**$* { *; }

-dontwarn com.jeremyliao.liveeventbus.**
-keep class com.jeremyliao.liveeventbus.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.arch.core.** { *; }

-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keepattributes Signature

