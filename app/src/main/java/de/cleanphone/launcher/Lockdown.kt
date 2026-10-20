package de.cleanphone.launcher

import android.app.Application
import android.app.WallpaperManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings

class AdminReceiver : DeviceAdminReceiver()

class CleanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apps installed while we run get hidden right away if they are not allowed.
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                Lockdown.sweep(c)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }
}

/**
 * The "full lock". Only works when the app was made device owner over USB
 * (see Einrichten.bat). Hidden apps are not deleted; [release] brings every one back.
 */
object Lockdown {

    private fun dpm(c: Context) = c.getSystemService(DevicePolicyManager::class.java)
    private fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)

    fun isOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)

    fun hiddenCount(c: Context) = c.prefs().getStringSet("hidden", emptySet())!!.size

    fun allowed(c: Context): Set<String> = Apps.systemEssentials(c) + Catalog.packages

    /** Apply the lock: we are the only home screen and browser, everything else with an icon is hidden. */
    fun apply(c: Context) {
        if (!isOwner(c)) return
        val dpm = dpm(c)
        val admin = admin(c)
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, home, ComponentName(c, HomeActivity::class.java))
        val web = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE); addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("http"); addDataScheme("https")
        }
        dpm.addPersistentPreferredActivity(admin, web, ComponentName(c, BrowserActivity::class.java))
        dpm.setUninstallBlocked(admin, c.packageName, true)
        sweep(c)
    }

    /** Hide every app with a launcher icon that is not on the allowed list. */
    fun sweep(c: Context) {
        if (!isOwner(c)) return
        val dpm = dpm(c)
        val admin = admin(c)
        val allowed = allowed(c)
        val hidden = c.prefs().getStringSet("hidden", emptySet())!!.toMutableSet()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pkgs = c.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.toSet()
        for (pkg in pkgs) {
            if (pkg in allowed) continue
            try {
                if (dpm.setApplicationHidden(admin, pkg, true)) hidden += pkg
            } catch (e: Exception) {
                // some system packages refuse; leave them
            }
        }
        c.prefs().edit().putStringSet("hidden", hidden).apply()
    }

    /** Undo everything so the phone is a normal Samsung again. */
    fun release(c: Context) {
        val p = c.prefs()
        if (isOwner(c)) {
            val dpm = dpm(c)
            val admin = admin(c)
            val all = c.packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                .map { it.packageName }.toSet() + p.getStringSet("hidden", emptySet())!!
            for (pkg in all) {
                try {
                    if (dpm.isApplicationHidden(admin, pkg)) dpm.setApplicationHidden(admin, pkg, false)
                } catch (e: Exception) {
                }
            }
            dpm.clearPackagePersistentPreferredActivities(admin, c.packageName)
            dpm.setUninstallBlocked(admin, c.packageName, false)
            dpm.clearDeviceOwnerApp(c.packageName)
        }
        setGrayscale(c, false)
        try {
            WallpaperManager.getInstance(c).clear(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        } catch (e: Exception) {
        }
        p.edit().clear().putBoolean("reset_done", true).putBoolean("first_done", true).apply()
    }

    fun canGrayscale(c: Context) =
        c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isGrayscale(c: Context) =
        Settings.Secure.getInt(c.contentResolver, "accessibility_display_daltonizer_enabled", 0) == 1 &&
            Settings.Secure.getInt(c.contentResolver, "accessibility_display_daltonizer", -1) == 0

    fun setGrayscale(c: Context, on: Boolean) {
        if (!canGrayscale(c)) return
        val cr = c.contentResolver
        Settings.Secure.putInt(cr, "accessibility_display_daltonizer_enabled", if (on) 1 else 0)
        if (on) Settings.Secure.putInt(cr, "accessibility_display_daltonizer", 0)
    }
}
