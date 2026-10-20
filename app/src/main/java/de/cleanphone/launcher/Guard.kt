package de.cleanphone.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Decides what belongs to Clean. Everything else stays installed and keeps its data
 * (logins, chats, ...) - it just cannot open or send notifications while Clean runs.
 */
object Guard {
    private var allowedCache: Set<String> = emptySet()
    private var launchableCache: Set<String> = emptySet()
    private var cacheTime = 0L

    /** System parts that must always be able to show notifications (calls, alarms, battery). */
    private val systemNotify = setOf(
        "android", "com.android.systemui", "com.android.phone", "com.android.server.telecom",
        "com.samsung.android.incallui", "com.android.providers.downloads",
    )

    /** Big platform apps that are allowed to run in the background but should stay quiet. */
    private val quiet = setOf(
        "com.google.android.gms", "com.google.android.googlequicksearchbox", "com.samsung.android.app.watchmanager",
    )

    private fun refresh(c: Context) {
        val now = System.currentTimeMillis()
        if (now - cacheTime < 15_000) return
        allowedCache = Lockdown.allowed(c)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        launchableCache = c.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.toSet()
        cacheTime = now
    }

    fun invalidate() { cacheTime = 0 }

    /** True when a notification from [pkg] should be removed. */
    fun blocksNotification(c: Context, pkg: String): Boolean {
        if (!c.prefs().getBoolean("notify_filter", true)) return false
        refresh(c)
        if (pkg in systemNotify) return false
        if (pkg in quiet) return true
        return pkg !in allowedCache
    }

    /** The Play Store only works in the background for Clean, it never opens on its own. */
    fun blocksPlay(c: Context) = c.prefs().getBoolean("guard_on", true)

    /** True when the app [pkg] must not be opened. */
    fun blocksApp(c: Context, pkg: String): Boolean {
        if (!c.prefs().getBoolean("guard_on", true)) return false
        refresh(c)
        return pkg in launchableCache && pkg !in allowedCache
    }

    // ---- is it switched on?

    private fun component(c: Context, cls: Class<*>) = ComponentName(c, cls).flattenToString()

    fun notifyEnabled(c: Context): Boolean =
        Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
            ?.contains(component(c, NotifyFilter::class.java)) == true

    fun appGuardEnabled(c: Context): Boolean =
        Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(component(c, AppGuard::class.java)) == true

    /**
     * When Einrichten.bat granted WRITE_SECURE_SETTINGS we can switch both guards on
     * ourselves. Otherwise the user does it once in the phone settings.
     */
    fun enableSilently(c: Context) {
        if (!Lockdown.canGrayscale(c)) return
        val cr = c.contentResolver
        runCatching {
            if (!notifyEnabled(c)) {
                val cur = Settings.Secure.getString(cr, "enabled_notification_listeners")
                val me = component(c, NotifyFilter::class.java)
                Settings.Secure.putString(cr, "enabled_notification_listeners", if (cur.isNullOrEmpty()) me else "$cur:$me")
            }
            if (!appGuardEnabled(c)) {
                val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                val me = component(c, AppGuard::class.java)
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (cur.isNullOrEmpty()) me else "$cur:$me")
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
        }
    }

    fun openNotifySettings(c: Context) = Apps.openSystem(c, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun openGuardSettings(c: Context) = Apps.openSystem(c, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun openAppInfo(c: Context) = Apps.openSystem(
        c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + c.packageName))
    )
}

/** Removes notifications from every app that is not part of Clean. */
class NotifyFilter : NotificationListenerService() {

    override fun onListenerConnected() {
        runCatching { activeNotifications?.forEach { check(it) } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = check(sbn)

    private fun check(sbn: StatusBarNotification) {
        if (!Guard.blocksNotification(this, sbn.packageName)) return
        cancelNotification(sbn.key)
        // Ongoing ones ignore cancel; hiding them for a long time works instead.
        if (sbn.isOngoing) runCatching { snoozeNotification(sbn.key, 365L * 24 * 3600 * 1000) }
    }
}

/**
 * Sends you back to Clean when an app that is not part of Clean opens, and taps
 * "Installieren" in Google Play for apps chosen in Clean's App Store.
 */
class AppGuard : AccessibilityService() {
    private var lastToast = 0L

    override fun onServiceConnected() { instance = this }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        /** The running service, so the installer can look at Google Play on its own schedule. */
        @Volatile var instance: AppGuard? = null
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == "com.android.vending") {
            if (Installer.playAllowed()) Installer.onPlayEvent(this)
            else if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && Guard.blocksPlay(this)) sendHome()
            return
        }
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (pkg == packageName || !Guard.blocksApp(this, pkg)) return
        sendHome()
    }

    private fun sendHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val now = System.currentTimeMillis()
        if (now - lastToast > 3000) {
            lastToast = now
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Diese App ist in Clean gesperrt", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInterrupt() {}
}
