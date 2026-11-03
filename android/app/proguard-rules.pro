# Keep WebKit and JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes JavascriptInterface
-keepclassmembers class com.godseye.view.mobile.MainActivity$GodsEyeNativeBridge {
    public *;
}
