package com.godseye.view.mobile

import com.godseye.view.mobile.R
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var customViewContainer: FrameLayout
    private lateinit var fabSettings: FloatingActionButton
    private lateinit var fabRefresh: FloatingActionButton
    private lateinit var fabFullscreen: FloatingActionButton
    private lateinit var prefs: SharedPreferences

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isImmersive = true
    private var backPressedTime: Long = 0

    companion object {
        private const val PREFS_NAME = "gods_eye_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_CESIUM_TOKEN = "cesium_token"
        private const val KEY_GOOGLE_KEY = "google_key"
        private const val KEY_KEEP_AWAKE = "keep_awake"
        private const val DEFAULT_ASSET_URL = "file:///android_asset/www/index.html"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        
        if (locationGranted) {
            triggerHaptic(50)
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('gev-native-location-ready'));", null)
        }
        if (audioGranted) {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('gev-native-audio-ready'));", null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Setup tactical fullscreen layout
        setContentView(R.layout.activity_main)
        setupViews()
        setupImmersiveMode()
        requestPermissionsIfNeeded()
        setupWebView()
        setupActionButtons()
        setupBackNavigation()

        loadGodsEye()
    }

    private fun setupViews() {
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        customViewContainer = findViewById(R.id.customViewContainer)
        fabSettings = findViewById(R.id.fabSettings)
        fabRefresh = findViewById(R.id.fabRefresh)
        fabFullscreen = findViewById(R.id.fabFullscreen)

        if (prefs.getBoolean(KEY_KEEP_AWAKE, true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setupImmersiveMode() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (isImmersive) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun requestPermissionsIfNeeded() {
        val neededPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            setGeolocationEnabled(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "${userAgentString} GodsEyeMobile/1.0.0 (Android Tactical Console)"
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isScrollbarFadingEnabled = true
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        // Bridge for native Android capabilities
        webView.addJavascriptInterface(GodsEyeNativeBridge(this), "GodsEyeNative")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    val requestedResources = it.resources
                    it.grant(requestedResources)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                customViewContainer.addView(view, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                customViewContainer.visibility = View.VISIBLE
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                customView?.let {
                    customViewContainer.removeView(it)
                    customView = null
                }
                customViewContainer.visibility = View.GONE
                webView.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                // Inject configured API keys into window object
                injectEnvironmentOverrides()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:///")) {
                    // Check if it's external link (e.g. docs, external site)
                    if (url.contains("github.com") || url.contains("twitter.com") || url.contains("x.com")) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(browserIntent)
                        return true
                    }
                    return false
                }
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    showFallbackErrorPage(error?.description?.toString() ?: "Network error")
                }
            }
        }
    }

    private fun injectEnvironmentOverrides() {
        val cesiumToken = prefs.getString(KEY_CESIUM_TOKEN, "") ?: ""
        val googleKey = prefs.getString(KEY_GOOGLE_KEY, "") ?: ""
        val script = """
            (function() {
                window.__GEV_ANDROID_NATIVE__ = true;
                if ("$cesiumToken") {
                    window.__CESIUM_ION_TOKEN__ = "$cesiumToken";
                }
                if ("$googleKey") {
                    window.__GOOGLE_MAPS_API_KEY__ = "$googleKey";
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun loadGodsEye() {
        val configuredUrl = prefs.getString(KEY_SERVER_URL, null)
        val targetUrl = if (!configuredUrl.isNullOrBlank()) configuredUrl else DEFAULT_ASSET_URL
        webView.loadUrl(targetUrl)
    }

    private fun showFallbackErrorPage(errorMsg: String) {
        val htmlData = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        background-color: #0a0a0f;
                        color: #00ffcc;
                        font-family: monospace;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        height: 100vh;
                        margin: 0;
                        padding: 20px;
                        box-sizing: border-box;
                        text-align: center;
                    }
                    h1 { color: #ff3366; font-size: 22px; letter-spacing: 2px; }
                    p { color: #a0aec0; font-size: 14px; max-width: 400px; }
                    .btn {
                        background: rgba(0, 255, 204, 0.15);
                        border: 1px solid #00ffcc;
                        color: #00ffcc;
                        padding: 12px 24px;
                        margin-top: 20px;
                        font-family: monospace;
                        font-size: 14px;
                        cursor: pointer;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                    }
                    .btn:active { background: #00ffcc; color: #000; }
                </style>
            </head>
            <body>
                <h1>[ CONNECTION SEVERED ]</h1>
                <p>Tactical uplink unable to reach stream endpoint: $errorMsg</p>
                <p>Tap below to reconnect or configure custom endpoint in Settings.</p>
                <button class="btn" onclick="GodsEyeNative.reload()">RE-ENGAGE UPLINK</button>
                <button class="btn" style="border-color:#ffb700;color:#ffb700;margin-top:10px;" onclick="GodsEyeNative.openSettings()">TACTICAL CONFIG</button>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("file:///android_asset/www/", htmlData, "text/html", "UTF-8", null)
    }

    private fun setupActionButtons() {
        fabSettings.setOnClickListener {
            triggerHaptic(30)
            showSettingsDialog()
        }

        fabRefresh.setOnClickListener {
            triggerHaptic(30)
            loadGodsEye()
        }

        fabFullscreen.setOnClickListener {
            triggerHaptic(30)
            isImmersive = !isImmersive
            setupImmersiveMode()
        }
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val editUrl = view.findViewById<TextInputEditText>(R.id.editServerUrl)
        val editCesium = view.findViewById<TextInputEditText>(R.id.editCesiumToken)
        val editGoogle = view.findViewById<TextInputEditText>(R.id.editGoogleKey)

        editUrl.setText(prefs.getString(KEY_SERVER_URL, ""))
        editCesium.setText(prefs.getString(KEY_CESIUM_TOKEN, ""))
        editGoogle.setText(prefs.getString(KEY_GOOGLE_KEY, ""))

        MaterialAlertDialogBuilder(this, R.style.Theme_GodsEyeMobile_Dialog)
            .setTitle(R.string.tactical_settings_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val newUrl = editUrl.text?.toString()?.trim() ?: ""
                val newCesium = editCesium.text?.toString()?.trim() ?: ""
                val newGoogle = editGoogle.text?.toString()?.trim() ?: ""

                prefs.edit()
                    .putString(KEY_SERVER_URL, newUrl)
                    .putString(KEY_CESIUM_TOKEN, newCesium)
                    .putString(KEY_GOOGLE_KEY, newGoogle)
                    .apply()

                Toast.makeText(this, "Tactical config updated. Reloading...", Toast.LENGTH_SHORT).show()
                loadGodsEye()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton("Reset Default") { _, _ ->
                prefs.edit().remove(KEY_SERVER_URL).apply()
                loadGodsEye()
            }
            .show()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
                    return
                }

                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }

                if (System.currentTimeMillis() - backPressedTime < 2000) {
                    finish()
                } else {
                    backPressedTime = System.currentTimeMillis()
                    Toast.makeText(this@MainActivity, "Press BACK again to exit God's Eye View", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    fun triggerHaptic(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    }

    inner class GodsEyeNativeBridge(private val context: Context) {
        @JavascriptInterface
        fun getPlatform(): String = "android"

        @JavascriptInterface
        fun getAppVersion(): String = "1.0.0"

        @JavascriptInterface
        fun triggerHaptic(ms: Long) {
            this@MainActivity.triggerHaptic(ms)
        }

        @JavascriptInterface
        fun reload() {
            runOnUiThread { this@MainActivity.loadGodsEye() }
        }

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread { this@MainActivity.showSettingsDialog() }
        }

        @JavascriptInterface
        fun toggleFullscreen() {
            runOnUiThread {
                isImmersive = !isImmersive
                setupImmersiveMode()
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
