package de.cleanphone.launcher

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

/**
 * "Laden" in Clean's App Store: opens the app in Google Play and - when the app lock
 * (accessibility service) is on - taps "Installieren" there by itself and comes straight
 * back, so the download simply runs in the background.
 */
object Installer {
    private const val PLAY = "com.android.vending"
    private val main = Handler(Looper.getMainLooper())

    /** Apps the user tapped "Laden" on, with the time of the tap. */
    private val loading = HashMap<String, Long>()

    @Volatile private var pending: String? = null
    @Volatile private var startedAt = 0L
    @Volatile private var done = false

    fun hasPlay(c: Context) = Apps.installed(c, PLAY)

    /** Clean only lets Google Play show itself while it is loading an app for you. */
    fun playAllowed() = pending != null && System.currentTimeMillis() - startedAt < 60_000

    fun isLoading(pkg: String): Boolean {
        val t = loading[pkg] ?: return false
        if (System.currentTimeMillis() - t > 10 * 60_000) { loading.remove(pkg); return false }
        return true
    }

    fun finished(pkg: String) { loading.remove(pkg) }

    fun install(c: Context, app: StoreApp) = install(c, app.pkg)

    fun install(c: Context, pkg: String) {
        if (!hasPlay(c)) {
            AlertDialog.Builder(c)
                .setTitle("Kein Google Play")
                .setMessage("Auf diesem Handy gibt es den Google Play Store nicht. Darum kann Clean hier keine Apps laden.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        // Known to be signed out: explain first instead of flashing the Play Store.
        if (c.prefs().getBoolean("play_signin_needed", false)) {
            SignInActivity.open(c, pkg)
            return
        }
        loading[pkg] = System.currentTimeMillis()
        if (Guard.appGuardEnabled(c)) {
            pending = pkg; startedAt = System.currentTimeMillis(); done = false
        }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
            .setPackage(PLAY)
            // Ask Play for its small sheet on top of Clean instead of the full store.
            .putExtra("overlay", true)
            .putExtra("callerId", c.packageName)
        if (c !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(i) }.onFailure {
            loading.remove(pkg)
            pending = null
        }
        // Look at the Play screen a few times per second; Play doesn't always report when it's ready.
        if (pending != null) main.postDelayed(poll, 500)
    }

    private val poll = object : Runnable {
        override fun run() {
            val svc = AppGuard.instance
            if (pending == null || done || svc == null) return
            onPlayEvent(svc)
            if (pending != null && !done) main.postDelayed(this, 400)
        }
    }

    /** Called by [AppGuard] for every screen change inside Google Play. */
    fun onPlayEvent(svc: AccessibilityService) {
        val pkg = pending ?: return
        if (done) return
        if (System.currentTimeMillis() - startedAt > 60_000) { pending = null; return }
        val roots = buildList {
            svc.rootInActiveWindow?.let { add(it) }
            runCatching { svc.windows.mapNotNull { it.root } }.getOrNull()?.let { addAll(it) }
        }.filter { it.packageName == PLAY }
        for (root in roots) {
            val install = find(root, "Installieren", "Install")
            if (install != null) {
                done = true
                svc.prefs().edit().putBoolean("play_signin_needed", false).apply()
                click(install)
                // Give Play a moment to start the download, then go back to Clean.
                main.postDelayed({ goBack(svc) }, 900)
                return
            }
            // Already installed or being installed: nothing to tap, just go back.
            if (find(root, "Öffnen", "Open", "Deinstallieren", "Uninstall", "Abbrechen", "Cancel") != null) {
                done = true
                svc.prefs().edit().putBoolean("play_signin_needed", false).apply()
                main.postDelayed({ goBack(svc) }, 300)
                return
            }
            // The download has started (the user tapped "Installieren" themselves): go back to Clean.
            if (findContaining(root, "Wird installiert", "Wird heruntergeladen", "Ausstehend", "Installing", "Downloading", "Pending") != null) {
                done = true
                main.postDelayed({ goBack(svc) }, 300)
                return
            }
            // Not signed in to Google: leave Play and show Clean's short sign-in guide.
            // (Google hides the sign-in button from other apps, so we recognise the text above it.)
            if (find(root, "Anmelden", "Sign in") != null ||
                findContaining(root, "Melde dich an", "Sign in to find") != null
            ) {
                done = true
                loading.remove(pkg)
                svc.prefs().edit().putBoolean("play_signin_needed", true).apply()
                goBack(svc)
                main.postDelayed({ SignInActivity.open(svc, pkg) }, 400)
                return
            }
        }
    }

    private fun goBack(svc: AccessibilityService) {
        pending = null
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    /** Walks the whole Play screen (the built-in text search misses some buttons). */
    private fun find(root: AccessibilityNodeInfo, vararg labels: String): AccessibilityNodeInfo? {
        val wanted = labels.map { it.lowercase() }
        fun visit(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (n == null || depth > 40) return null
            val t = (n.text ?: n.contentDescription)?.toString()?.trim()?.lowercase()
            if (t != null && t in wanted && n.isVisibleToUser && n.isEnabled) return n
            for (i in 0 until n.childCount) visit(n.getChild(i), depth + 1)?.let { return it }
            return null
        }
        return visit(root, 0)
    }

    private fun findContaining(root: AccessibilityNodeInfo, vararg parts: String): AccessibilityNodeInfo? {
        val wanted = parts.map { it.lowercase() }
        fun visit(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (n == null || depth > 40) return null
            val t = (n.text ?: n.contentDescription)?.toString()?.lowercase()
            if (t != null && wanted.any { t.contains(it) } && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) visit(n.getChild(i), depth + 1)?.let { return it }
            return null
        }
        return visit(root, 0)
    }

    private fun click(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        (n ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
