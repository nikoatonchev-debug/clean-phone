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

    fun isLoading(pkg: String): Boolean {
        val t = loading[pkg] ?: return false
        if (System.currentTimeMillis() - t > 10 * 60_000) { loading.remove(pkg); return false }
        return true
    }

    fun finished(pkg: String) { loading.remove(pkg) }

    fun install(c: Context, app: StoreApp) {
        if (!hasPlay(c)) {
            AlertDialog.Builder(c)
                .setTitle("Kein Google Play")
                .setMessage("Auf diesem Handy gibt es den Google Play Store nicht. Darum kann Clean hier keine Apps laden.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        loading[app.pkg] = System.currentTimeMillis()
        if (Guard.appGuardEnabled(c)) {
            pending = app.pkg; startedAt = System.currentTimeMillis(); done = false
        }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.pkg}"))
            .setPackage(PLAY)
            // Ask Play for its small sheet on top of Clean instead of the full store.
            .putExtra("overlay", true)
            .putExtra("callerId", c.packageName)
        runCatching { c.startActivity(i) }.onFailure {
            loading.remove(app.pkg)
            pending = null
        }
    }

    /** Called by [AppGuard] for every screen change inside Google Play. */
    fun onPlayEvent(svc: AccessibilityService) {
        if (pending == null || done) return
        if (System.currentTimeMillis() - startedAt > 30_000) { pending = null; return }
        val roots = buildList {
            svc.rootInActiveWindow?.let { add(it) }
            runCatching { svc.windows.mapNotNull { it.root } }.getOrNull()?.let { addAll(it) }
        }.filter { it.packageName == PLAY }
        for (root in roots) {
            val install = find(root, "Installieren", "Install")
            if (install != null) {
                done = true
                click(install)
                // Give Play a moment to start the download, then go back to Clean.
                main.postDelayed({ goBack(svc) }, 900)
                return
            }
            // Already installed or being installed: nothing to tap, just go back.
            if (find(root, "Öffnen", "Open", "Deinstallieren", "Uninstall", "Abbrechen", "Cancel") != null) {
                done = true
                main.postDelayed({ goBack(svc) }, 300)
                return
            }
            // Not signed in to Google: leave Play open so the user can sign in once.
            if (find(root, "Anmelden", "Sign in") != null) {
                pending?.let { loading.remove(it) }
                pending = null
                return
            }
        }
    }

    private fun goBack(svc: AccessibilityService) {
        pending = null
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun find(root: AccessibilityNodeInfo, vararg labels: String): AccessibilityNodeInfo? {
        for (label in labels) {
            val hits = runCatching { root.findAccessibilityNodeInfosByText(label) }.getOrNull() ?: continue
            hits.firstOrNull { n ->
                n.isVisibleToUser && n.isEnabled &&
                    (n.text?.toString()?.trim().equals(label, true) || n.contentDescription?.toString()?.trim().equals(label, true))
            }?.let { return it }
        }
        return null
    }

    private fun click(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        (n ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
