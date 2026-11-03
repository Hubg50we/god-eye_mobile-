package com.godseye.view.mobile

import android.app.Application
import android.os.Build
import android.webkit.WebView

class GodsEyeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Ensure multi-process webview compatibility if required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getProcessName()
            if (packageName != processName) {
                WebView.setDataDirectorySuffix(processName)
            }
        }
    }
}
