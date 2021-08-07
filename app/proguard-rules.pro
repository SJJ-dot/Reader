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
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

-keep class android.support.**{*;}

 -keep class com.gyf.immersionbar.* {*;}
 -dontwarn com.gyf.immersionbar.**
-keep class org.mozilla.javascript.** { *; }
-keep class com.sjianjun.reader.view.** { *; }
-keep class com.sjianjun.reader.module.bookcity.** { *; }
-keep class com.sjianjun.reader.bean.** { *; }
-keep class org.jsoup.Jsoup { *; }
-keep class org.jsoup.** { *; }
-keep class sjj.alog.Log { *; }
-keep class com.sjianjun.reader.bean.SearchResult { *; }
-keep class com.sjianjun.reader.bean.Chapter { *; }
-keep class com.sjianjun.reader.bean.Book { *; }
-keep class org.jsoup.internal.StringUtil { *; }
-keep class com.sjianjun.reader.rhino.** { *; }
-keep class com.sjianjun.reader.view.CustomWebView$AdBlock { *; }
-keep class com.sjianjun.reader.http.Http { *; }
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
