package com.example.buswatch

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.cloudinary.android.MediaManager
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.auth.FirebaseAuth
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import org.osmdroid.config.Configuration

class BusWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. Global Crash Logger
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("FATAL_APP_CRASH", "CRASH IN THREAD: ${thread.name}", throwable)
            oldHandler?.uncaughtException(thread, throwable)
        }

        // 2. Initialize GMS
        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (t: Throwable) {
            Log.e("BusWatchApp", "GMS Provider installation failed: ${t.message}")
        }

        // 3. OSRM Routing Configuration
        // We set a unique User-Agent to comply with OSRM/OSM usage policies
        Configuration.getInstance().userAgentValue = "BusWatch-Android-App/1.0 (com.example.buswatch; contact: buswatch2@gmail.com)"

        // 4. OneSignal & Cloudinary
        try {
            OneSignal.Debug.logLevel = LogLevel.WARN
            OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
            FirebaseAuth.getInstance().currentUser?.uid?.let { OneSignal.login(it) }

            val cloudinaryConfig = mapOf(
                "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
                "api_key" to BuildConfig.CLOUDINARY_API_KEY,
                "api_secret" to BuildConfig.CLOUDINARY_API_SECRET
            )
            MediaManager.init(this, cloudinaryConfig)
        } catch (e: Exception) {
            Log.e("BusWatchApp", "Services Init Warning: ${e.message}")
        }

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "BusWatch Notifications"
            val channelId = "BUSWATCH_NOTIF"
            val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for bus arrival and student boarding"
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
