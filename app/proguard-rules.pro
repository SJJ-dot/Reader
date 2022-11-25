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

 -keep class com.gyf.immersionbar.* {*;}
 -dontwarn com.gyf.immersionbar.**
-keep class org.mozilla.javascript.** { *; }
-keep class com.sjianjun.reader.** { *; }
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
