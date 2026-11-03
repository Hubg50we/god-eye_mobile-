package com.hubg50we.godseye;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.ApplicationInfo;
import android.hardware.camera2.CameraManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.AlarmClock;
import android.app.SearchManager;

import android.media.AudioManager;
import android.os.BatteryManager;
import android.content.IntentFilter;
import android.app.ActivityManager;
import android.os.StatFs;
import android.os.Environment;
import android.os.SystemClock;
import android.os.PowerManager;
import android.net.ConnectivityManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.content.res.Configuration;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;

import java.util.List;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextToSpeech tts;
    private static final int SPEECH_REQUEST_CODE = 202;
    private long backPressedTime = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAssistIntent(intent);
    }

    private void handleAssistIntent(Intent intent) {
        if (intent != null && (Intent.ACTION_ASSIST.equals(intent.getAction()) || Intent.ACTION_VOICE_COMMAND.equals(intent.getAction()))) {
            if (webView != null) {
                webView.evaluateJavascript("if (typeof triggerAssistantSummon === 'function') triggerAssistantSummon();", null);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen awake for tactical operations
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Initialize Native Android TextToSpeech Engine
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    tts.setLanguage(Locale.US);
                    tts.setSpeechRate(1.02f);
                }
            });
        } catch (Exception ignored) {}

                // Notch / Cutout display handling for edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // Fullscreen immersive mode
        hideSystemUI();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setGeolocationEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.addJavascriptInterface(new GodsEyeNativeBridge(this), "GodsEyeNative");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.postDelayed(() -> handleAssistIntent(getIntent()), 1000);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                // Auto-recover from any unreachable dev URL or network error
                if (failingUrl != null && !failingUrl.startsWith("file:///android_asset/")) {
                    view.post(() -> {
                        Toast.makeText(context, "Dev server unreachable. Returning to Local Tactical HUD.", Toast.LENGTH_SHORT).show();
                        view.loadUrl("file:///android_asset/web/index.html");
                    });
                }
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    String failingUrl = request.getUrl() != null ? request.getUrl().toString() : "";
                    if (!failingUrl.startsWith("file:///android_asset/")) {
                        view.post(() -> {
                            Toast.makeText(context, "Connection refused. Resuming Local Tactical HUD.", Toast.LENGTH_SHORT).show();
                            view.loadUrl("file:///android_asset/web/index.html");
                        });
                    }
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return false;

                // Stay in local asset app
                if (url.startsWith("file:///android_asset/")) {
                    return false;
                }

                // Block accidental localhost/10.0.2.2 dev redirects from destroying local app state
                if (url.contains("10.0.2.2") || url.contains("localhost") || url.contains("127.0.0.1")) {
                    Toast.makeText(context, "Local dev server unreachable. Staying on local HUD.", Toast.LENGTH_SHORT).show();
                    return true;
                }

                // External web links open safely in external browser
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
                        @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.postDelayed(() -> handleAssistIntent(getIntent()), 1000);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:///")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        requestPermissionsIfNeeded();

        // Load the mobile web console
        webView.loadUrl("file:///android_asset/web/index.html");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    if (System.currentTimeMillis() - backPressedTime < 2000) {
                        finish();
                    } else {
                        backPressedTime = System.currentTimeMillis();
                        Toast.makeText(MainActivity.this, "Press BACK again to exit God's Eye View", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        hideSystemUI();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0)
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\"", "\\\"")
                        .replace("\n", " ")
                        .replace("\r", "");
                    if (webView != null) {
                        webView.evaluateJavascript("if (window.onNativeSpeechResult) window.onNativeSpeechResult('" + spokenText + "');", null);
                    }
                }
            } else {
                if (webView != null) {
                    webView.evaluateJavascript("if (window.onNativeSpeechCancel) window.onNativeSpeechCancel();", null);
                }
            }
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void requestPermissionsIfNeeded() {
        String[] permissions = new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO
        };
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 101);
                break;
            }
        }
    }

    public void triggerHaptic(long durationMs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } else {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(durationMs);
            }
        }
    }

    public class GodsEyeNativeBridge {
        private final Context context;

        public GodsEyeNativeBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public String getDeviceOptimizationProfile() {
            try {
                ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                if (actManager != null) actManager.getMemoryInfo(memInfo);

                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                boolean isPowerSave = (pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) && pm.isPowerSaveMode();
                boolean isLowRam = actManager != null && actManager.isLowRamDevice();

                int cores = Runtime.getRuntime().availableProcessors();
                long totalRamMB = memInfo.totalMem / (1024 * 1024);
                long freeRamMB = memInfo.availMem / (1024 * 1024);

                DisplayMetrics dm = getResources().getDisplayMetrics();
                int screenWidth = dm.widthPixels;
                int screenHeight = dm.heightPixels;
                float density = dm.density;

                boolean isTablet = (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;

                float refreshRate = 60.0f;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Display display = getDisplay();
                    if (display != null) refreshRate = display.getRefreshRate();
                }

                String tier = "high";
                if (isLowRam || totalRamMB < 2500 || cores <= 4 || isPowerSave) {
                    tier = "low";
                } else if (totalRamMB < 5500 || cores <= 6) {
                    tier = "medium";
                } else if (totalRamMB >= 8000 && cores >= 8 && refreshRate >= 90) {
                    tier = "flagship";
                }

                JSONObject obj = new JSONObject();
                obj.put("tier", tier);
                obj.put("isLowRam", isLowRam);
                obj.put("isPowerSave", isPowerSave);
                obj.put("cores", cores);
                obj.put("totalRamMB", totalRamMB);
                obj.put("freeRamMB", freeRamMB);
                obj.put("screenWidth", screenWidth);
                obj.put("screenHeight", screenHeight);
                obj.put("density", density);
                obj.put("isTablet", isTablet);
                obj.put("refreshRate", Math.round(refreshRate));
                return obj.toString();
            } catch (Exception e) {
                return "{\"tier\":\"medium\",\"isLowRam\":false,\"isPowerSave\":false,\"refreshRate\":60}";
            }
        }

        @JavascriptInterface
        public void setTargetFps(int fps) {
            // Dynamic refresh rate hook for supported Android displays
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        // Allows system display engine to adapt frame timing
                        getWindow().setAttributes(lp);
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
                @JavascriptInterface
        public String getThermalStatus() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        int status = pm.getCurrentThermalStatus();
                        String str = "NOMINAL";
                        if (status == PowerManager.THERMAL_STATUS_LIGHT) str = "LIGHT";
                        else if (status == PowerManager.THERMAL_STATUS_MODERATE) str = "MODERATE";
                        else if (status == PowerManager.THERMAL_STATUS_SEVERE) str = "SEVERE";
                        else if (status == PowerManager.THERMAL_STATUS_CRITICAL) str = "CRITICAL";
                        else if (status == PowerManager.THERMAL_STATUS_EMERGENCY) str = "EMERGENCY";
                        return "{\"status\":\"" + str + "\",\"code\":" + status + "}";
                    }
                } catch (Exception ignored) {}
            }
            return "{\"status\":\"NOMINAL\",\"code\":0}";
        }

        @JavascriptInterface
        public boolean isMeteredConnection() {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    return cm.isActiveNetworkMetered();
                }
            } catch (Exception ignored) {}
            return false;
        }

        @JavascriptInterface
        public String getPlatform() {
            return "android";
        }

        @JavascriptInterface
        public void triggerHaptic(long ms) {
            MainActivity.this.triggerHaptic(ms);
        }

        @JavascriptInterface
        public void reload() {
            runOnUiThread(() -> webView.reload());
        }

        @JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void copyToClipboard(String text) {
            runOnUiThread(() -> {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("God's Eye Intel", text);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void shareText(String title, String text) {
            runOnUiThread(() -> {
                try {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, text);
                    sendIntent.putExtra(Intent.EXTRA_TITLE, title);
                    sendIntent.setType("text/plain");
                    Intent shareIntent = Intent.createChooser(sendIntent, title);
                    startActivity(shareIntent);
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void setKeepScreenOn(boolean keepOn) {
            runOnUiThread(() -> {
                if (keepOn) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public void startVoiceListening() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString());
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "AEGIS Tactical Voice Assistant");
                    startActivityForResult(intent, SPEECH_REQUEST_CODE);
                } catch (Exception e) {
                    Toast.makeText(context, "Voice input: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    if (webView != null) {
                        webView.evaluateJavascript("if (window.onNativeSpeechError) window.onNativeSpeechError('" + e.getMessage().replace("'", "\\'") + "');", null);
                    }
                }
            });
        }

        @JavascriptInterface
        public void speakText(String text) {
            runOnUiThread(() -> {
                if (tts != null) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GEV_TTS");
                }
            });
        }

        @JavascriptInterface
        public void stopSpeaking() {
            runOnUiThread(() -> {
                if (tts != null) {
                    tts.stop();
                }
            });
        }

        @JavascriptInterface
        public boolean openApp(String appName) {
            if (appName == null || appName.trim().isEmpty()) return false;
            final String target = appName.trim().toLowerCase();

            runOnUiThread(() -> {
                Intent launchIntent = null;
                try {
                    PackageManager pm = getPackageManager();

                    // 1. Priority Aliases (YouTube, Maps, Camera, Chrome, Spotify, WhatsApp, Settings)
                    if (target.equals("youtube") || target.equals("yt")) {
                        launchIntent = pm.getLaunchIntentForPackage("com.google.android.youtube");
                        if (launchIntent == null) {
                            launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"));
                        }
                    } else if (target.equals("maps") || target.equals("google maps")) {
                        launchIntent = pm.getLaunchIntentForPackage("com.google.android.apps.maps");
                        if (launchIntent == null) {
                            launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="));
                        }
                    } else if (target.equals("camera")) {
                        launchIntent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                    } else if (target.equals("chrome") || target.equals("browser")) {
                        launchIntent = pm.getLaunchIntentForPackage("com.android.chrome");
                        if (launchIntent == null) {
                            launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
                        }
                    } else if (target.equals("spotify") || target.equals("music")) {
                        launchIntent = pm.getLaunchIntentForPackage("com.spotify.music");
                    } else if (target.equals("whatsapp")) {
                        launchIntent = pm.getLaunchIntentForPackage("com.whatsapp");
                    } else if (target.equals("calculator")) {
                        launchIntent = pm.getLaunchIntentForPackage("com.google.android.calculator");
                        if (launchIntent == null) {
                            launchIntent = pm.getLaunchIntentForPackage("com.android.calculator2");
                        }
                    } else if (target.equals("settings")) {
                        launchIntent = new Intent(Settings.ACTION_SETTINGS);
                    } else if (target.equals("gmail") || target.equals("email")) {
                        launchIntent = pm.getLaunchIntentForPackage("com.google.android.gm");
                    } else if (target.equals("play store") || target.equals("store")) {
                        launchIntent = pm.getLaunchIntentForPackage("com.android.vending");
                    } else if (target.equals("photos") || target.equals("gallery")) {
                        launchIntent = pm.getLaunchIntentForPackage("com.google.android.apps.photos");
                    } else {
                        // Dynamic package look up or installed label match
                        launchIntent = pm.getLaunchIntentForPackage(target);
                        if (launchIntent == null) {
                            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                            for (ApplicationInfo ai : apps) {
                                String label = pm.getApplicationLabel(ai).toString().toLowerCase();
                                if (label.contains(target) || ai.packageName.toLowerCase().contains(target)) {
                                    launchIntent = pm.getLaunchIntentForPackage(ai.packageName);
                                    if (launchIntent != null) break;
                                }
                            }
                        }
                    }

                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launchIntent);
                        Toast.makeText(context, "J.A.R.V.I.S.: Opening " + appName, Toast.LENGTH_SHORT).show();
                    } else {
                        Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(appName)));
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(fallback);
                    }
                } catch (Exception e) {
                    Toast.makeText(context, "Could not open " + appName + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean toggleFlashlight(boolean enable) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                        if (camManager != null && camManager.getCameraIdList().length > 0) {
                            String cameraId = camManager.getCameraIdList()[0];
                            camManager.setTorchMode(cameraId, enable);
                            Toast.makeText(context, "Flashlight " + (enable ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(context, "Flashlight error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean makeCall(String number) {
            if (number == null || number.trim().isEmpty()) return false;
            runOnUiThread(() -> {
                try {
                    Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.trim()));
                    callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(callIntent);
                } catch (Exception e) {
                    Toast.makeText(context, "Dialer error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }


        @JavascriptInterface
        public String setVolume(int percent) {
            final int pct = Math.max(0, Math.min(100, percent));
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int targetVol = (int) Math.round((maxVol * pct) / 100.0);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI);
                    return "Volume set to " + pct + "% (level " + targetVol + "/" + maxVol + ")";
                }
            } catch (Exception e) {
                return "Volume error: " + e.getMessage();
            }
            return "Audio service unavailable";
        }

        @JavascriptInterface
        public String adjustVolume(int direction) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int dir = direction > 0 ? AudioManager.ADJUST_RAISE : (direction < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_SAME);
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
                    int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int pct = Math.round((cur * 100.0f) / max);
                    return "Volume adjusted: " + pct + "%";
                }
            } catch (Exception e) {
                return "Adjust error: " + e.getMessage();
            }
            return "Volume unchanged";
        }

        @JavascriptInterface
        public String muteAudio(boolean mute) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                    } else {
                        am.setStreamMute(AudioManager.STREAM_MUSIC, mute);
                    }
                    return mute ? "Audio muted" : "Audio unmuted";
                }
            } catch (Exception e) {
                return "Mute error: " + e.getMessage();
            }
            return "Audio state unchanged";
        }

        @JavascriptInterface
        public String getBatteryStatus() {
            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent b = registerReceiver(null, ifilter);
                if (b != null) {
                    int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    int temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                    int voltage = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                    int health = b.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);

                    boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                         status == BatteryManager.BATTERY_STATUS_FULL;
                    float pct = (level * 100.0f) / scale;
                    float tempC = temp / 10.0f;
                    float voltV = voltage / 1000.0f;

                    String healthStr = "Good";
                    if (health == BatteryManager.BATTERY_HEALTH_OVERHEAT) healthStr = "Overheat";
                    else if (health == BatteryManager.BATTERY_HEALTH_DEAD) healthStr = "Dead";
                    else if (health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) healthStr = "Over Voltage";

                    JSONObject json = new JSONObject();
                    json.put("level", Math.round(pct));
                    json.put("isCharging", isCharging);
                    json.put("temperatureC", tempC);
                    json.put("voltageV", voltV);
                    json.put("health", healthStr);
                    return json.toString();
                }
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
            return "{\"level\": 100, \"isCharging\": false, \"health\": \"Unknown\"}";
        }

        @JavascriptInterface
        public String getSystemDiagnostics() {
            try {
                ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                if (actManager != null) {
                    actManager.getMemoryInfo(memInfo);
                }

                StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
                long blockSize = stat.getBlockSizeLong();
                long totalBlocks = stat.getBlockCountLong();
                long availableBlocks = stat.getAvailableBlocksLong();

                double totalStorageGB = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0);
                double freeStorageGB = (availableBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0);
                double totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0);
                double freeRamGB = memInfo.availMem / (1024.0 * 1024.0 * 1024.0);
                double uptimeHours = SystemClock.elapsedRealtime() / (1000.0 * 3600.0);

                JSONObject json = new JSONObject();
                json.put("deviceModel", Build.MANUFACTURER.toUpperCase() + " " + Build.MODEL);
                json.put("androidVersion", Build.VERSION.RELEASE);
                json.put("sdkInt", Build.VERSION.SDK_INT);
                json.put("totalRamGB", Math.round(totalRamGB * 10.0) / 10.0);
                json.put("freeRamGB", Math.round(freeRamGB * 10.0) / 10.0);
                json.put("ramUsagePct", Math.round(((memInfo.totalMem - memInfo.availMem) * 100.0) / memInfo.totalMem));
                json.put("totalStorageGB", Math.round(totalStorageGB * 10.0) / 10.0);
                json.put("freeStorageGB", Math.round(freeStorageGB * 10.0) / 10.0);
                json.put("uptimeHours", Math.round(uptimeHours * 10.0) / 10.0);
                return json.toString();
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }

        @JavascriptInterface
        public boolean triggerTacticalHaptic(String pattern) {
            runOnUiThread(() -> {
                try {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null && v.hasVibrator()) {
                        String p = (pattern == null ? "pulse" : pattern).toLowerCase();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            if (p.equals("double")) {
                                long[] timings = {0, 40, 60, 40};
                                int[] amplitudes = {0, 200, 0, 255};
                                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                            } else if (p.equals("alert")) {
                                long[] timings = {0, 80, 50, 150};
                                int[] amplitudes = {0, 255, 0, 255};
                                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                            } else if (p.equals("reactor")) {
                                long[] timings = {0, 30, 40, 30, 40, 50};
                                int[] amplitudes = {0, 150, 0, 200, 0, 255};
                                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                            } else {
                                v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                            }
                        } else {
                            v.vibrate(60);
                        }
                    }
                } catch (Exception ignored) {}
            });
            return true;
        }

        @JavascriptInterface
        public boolean openSystemSetting(String settingType) {
            final String target = settingType == null ? "general" : settingType.toLowerCase().trim();
            runOnUiThread(() -> {
                try {
                    Intent intent;
                    if (target.contains("wifi") || target.contains("wi-fi")) {
                        intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    } else if (target.contains("bluetooth")) {
                        intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    } else if (target.contains("display") || target.contains("screen") || target.contains("brightness")) {
                        intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                    } else if (target.contains("sound") || target.contains("audio") || target.contains("volume")) {
                        intent = new Intent(Settings.ACTION_SOUND_SETTINGS);
                    } else if (target.contains("airplane")) {
                        intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
                    } else if (target.contains("battery") || target.contains("power")) {
                        intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
                    } else if (target.contains("app")) {
                        intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
                    } else {
                        intent = new Intent(Settings.ACTION_SETTINGS);
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, "Could not open settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean copyToClipboard(String text) {
            if (text == null) return false;
            runOnUiThread(() -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        ClipData clip = ClipData.newPlainText("JARVIS", text);
                        cm.setPrimaryClip(clip);
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ignored) {}
            });
            return true;
        }

        @JavascriptInterface
        public String readFromClipboard() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                    if (text != null) return text.toString();
                }
            } catch (Exception ignored) {}
            return "";
        }

        @JavascriptInterface
        public boolean setKeepScreenAwake(boolean awake) {
            runOnUiThread(() -> {
                try {
                    if (awake) {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        Toast.makeText(context, "J.A.R.V.I.S.: Screen awake locked", Toast.LENGTH_SHORT).show();
                    } else {
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        Toast.makeText(context, "J.A.R.V.I.S.: Normal screen sleep restored", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ignored) {}
            });
            return true;
        }

        @JavascriptInterface
        public boolean setScreenBrightness(float brightness) {
            final float b = Math.max(0.01f, Math.min(1.0f, brightness));
            runOnUiThread(() -> {
                try {
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = b;
                    getWindow().setAttributes(lp);
                } catch (Exception ignored) {}
            });
            return true;
        }

        @JavascriptInterface
        public boolean postSystemNotification(String title, String message) {
            runOnUiThread(() -> {
                try {
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    String channelId = "jarvis_sentinel_channel";
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                        NotificationChannel channel = new NotificationChannel(
                            channelId,
                            "J.A.R.V.I.S. Sentinel Alerts",
                            NotificationManager.IMPORTANCE_HIGH
                        );
                        channel.setDescription("Tactical alert notifications from J.A.R.V.I.S. OS");
                        nm.createNotificationChannel(channel);
                    }

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title != null ? title : "J.A.R.V.I.S. Tactical Sentinel")
                        .setContentText(message != null ? message : "Alert dispatched")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

                    if (nm != null) {
                        nm.notify((int) System.currentTimeMillis(), builder.build());
                    }
                } catch (Exception ignored) {}
            });
            return true;
        }


        @JavascriptInterface
        public boolean setTimer(int seconds, String label) {
            final int sec = Math.max(1, seconds);
            final String msg = label != null ? label : "J.A.R.V.I.S. Timer";
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
                    intent.putExtra(AlarmClock.EXTRA_LENGTH, sec);
                    intent.putExtra(AlarmClock.EXTRA_MESSAGE, msg);
                    intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Toast.makeText(context, "Timer set for " + sec + " seconds", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(context, "Timer error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setAlarm(int hour, int minute, String label) {
            final int h = Math.max(0, Math.min(23, hour));
            final int m = Math.max(0, Math.min(59, minute));
            final String msg = label != null ? label : "J.A.R.V.I.S. Alarm";
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
                    intent.putExtra(AlarmClock.EXTRA_HOUR, h);
                    intent.putExtra(AlarmClock.EXTRA_MINUTES, m);
                    intent.putExtra(AlarmClock.EXTRA_MESSAGE, msg);
                    intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Toast.makeText(context, "Alarm set for " + String.format(Locale.US, "%02d:%02d", h, m), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(context, "Alarm error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean searchWeb(String query) {
            if (query == null || query.trim().isEmpty()) return false;
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
                    intent.putExtra(SearchManager.QUERY, query.trim());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(fallback);
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean composeSms(String number, String message) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + (number != null ? number.trim() : "")));
                    if (message != null) intent.putExtra("sms_body", message);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, "SMS error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }


        @JavascriptInterface
        public boolean setVoiceLocale(String languageTag, float pitch, float rate) {
            runOnUiThread(() -> {
                try {
                    if (tts != null) {
                        Locale loc = Locale.forLanguageTag(languageTag != null ? languageTag : "en-GB");
                        tts.setLanguage(loc);
                        tts.setPitch(pitch > 0 ? pitch : 1.0f);
                        tts.setSpeechRate(rate > 0 ? rate : 1.0f);
                    }
                } catch (Exception ignored) {}
            });
            return true;
        }

        @JavascriptInterface
        public String getAppVersion() {
            return "3.2.0";
        }
    }
}
