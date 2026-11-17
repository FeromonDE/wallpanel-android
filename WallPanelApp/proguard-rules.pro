# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\raimund\AppData\Local\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keepclassmembernames class io.netty.** { *; }
-keepclassmembers class org.jctools.** { *; }
# Preserve runtime annotations and signatures used by lifecycle/JSON adapters.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Netty reads these fields through reflection/Unsafe.
-keepclassmembers,allowoptimization class io.netty.** {
    <fields>;
}

# Reflectively constructed XML views, including the custom screensaver.
-keep,allowoptimization class xyz.wallpanel.app.ui.views.** extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# WebView JavaScript bridges must retain their annotated entry points.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# AndroidAsync's UTF-8 compatibility path accesses this class reflectively.
-keepclassmembers class com.koushikdutta.async.util.Charsets {
    public static java.nio.charset.Charset US_ASCII;
}
