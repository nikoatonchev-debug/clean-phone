package de.cleanphone.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * First start: a calm "Clean" greeting, then four simple setup steps with check marks.
 * Shown again from the home screen until every step is done.
 */
class WelcomeActivity : Activity() {
    private lateinit var pal: Palette
    private lateinit var frame: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette.of(this)
        applySystemBars(pal)
        frame = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        setContentView(frame)
        if (intent.getBooleanExtra("setup", false)) showSetup() else showHello()
    }

    override fun onResume() {
        super.onResume()
        if (frame.tag == "setup") showSetup()
    }

    private fun fadeUp(v: View, delay: Long) {
        v.alpha = 0f; v.translationY = dp(16).toFloat()
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(700)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun showHello() {
        frame.removeAllViews()
        frame.tag = "hello"
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40), 0, dp(40), dp(80))
        }
        val title = text("Clean", 56f, pal.text, weight = 600).apply { gravity = Gravity.CENTER }
        val sub = text("Weniger Handy.\nMehr Leben.", 20f, pal.sub, weight = 400).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(14), 0, 0)
        }
        col.addView(title); col.addView(sub)
        frame.addView(col, FrameLayout.LayoutParams(-1, -1))
        val go = pill("Los geht's", pal, primary = true) { showSetup() }.apply {
            textSize = 16f
            setPadding(dp(32), dp(15), dp(32), dp(15))
            background = roundRect(pal.primary, dp(28).toFloat())
        }
        frame.addView(go, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(72)
        })
        fadeUp(title, 150); fadeUp(sub, 450); fadeUp(go, 800)
    }

    private fun showSetup() {
        val first = frame.tag != "setup"
        frame.removeAllViews()
        frame.tag = "setup"
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(40))
        }
        col.addView(text("Fast fertig", 34f, pal.text, weight = 700))
        col.addView(text("Vier kleine Schritte, dann ist dein Handy ganz clean.", 16f, pal.sub).apply {
            setPadding(0, dp(10), 0, dp(26))
            setLineSpacing(dp(3).toFloat(), 1f)
        })

        val steps = listOf(
            Step("Als Startbildschirm", "Clean erscheint, wenn du auf Home drückst", HomeActivity.isDefaultHome(this)) { askDefaultHome() },
            Step("Fremde Mitteilungen blockieren", "Nur Anrufe, SMS, Wecker und deine Apps", Guard.notifyEnabled(this)) { explainNotify() },
            Step("Andere Apps sperren", "YouTube & Co. bleiben drauf, gehen aber nicht auf", Guard.appGuardEnabled(this) || Lockdown.isOwner(this)) { explainGuard() },
            Step("Wetter und Schritte", "Standort und Bewegung erlauben", permissionsDone(this)) { askPermissions() },
        )
        steps.forEachIndexed { i, s ->
            val v = stepView(s)
            col.addView(v, lp().apply { bottomMargin = dp(10) })
            if (first) fadeUp(v, 80L + i * 70L)
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(col, FrameLayout.LayoutParams(-1, -2))
        }
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val done = pill(if (steps.all { it.done }) "Fertig" else "Später", pal, primary = steps.all { it.done }) { finish() }.apply {
            textSize = 16f
            setPadding(dp(32), dp(15), dp(32), dp(15))
        }
        frame.addView(done, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(48)
        })
    }

    private class Step(val title: String, val info: String, val done: Boolean, val action: () -> Unit)

    private fun stepView(s: Step): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = roundRect(pal.card, dp(20).toFloat())
        softPress(0.97f)
        setOnClickListener { if (!s.done) s.action() }
        val texts = LinearLayout(this@WelcomeActivity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(s.title, 16f, pal.text, weight = 600))
        texts.addView(text(s.info, 13f, pal.sub).apply { setPadding(0, dp(4), 0, 0) })
        addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val mark = FrameLayout(this@WelcomeActivity).apply {
            background = if (s.done) roundRect(pal.primary, dp(14).toFloat())
            else roundRect(0, dp(14).toFloat(), dp(2), pal.line)
        }
        if (s.done) mark.addView(ImageView(this@WelcomeActivity).apply {
            setImageDrawable(GlyphIcon(Glyph.CHECK, pal.onPrimary, tile = false))
        }, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
        addView(mark, LinearLayout.LayoutParams(dp(28), dp(28)))
    }

    // ---- actions

    private fun askDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                @Suppress("DEPRECATION")
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 2)
                return
            }
        }
        Apps.openSystem(this, Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun explainNotify() = dialog(
        "Mitteilungen blockieren",
        "Gleich öffnet sich eine Liste. Tippe dort auf „Clean – Mitteilungen filtern“ und schalte es ein.\n\n" +
            "Steht dort „Eingeschränkte Einstellung“: Tippe unten auf „App-Info“, dann oben rechts auf ⋮ und auf „Eingeschränkte Einstellungen zulassen“. Danach nochmal versuchen.",
        { Guard.openNotifySettings(this) }
    )

    private fun explainGuard() = dialog(
        "Andere Apps sperren",
        "Gleich öffnen sich die Bedienungshilfen. Tippe dort auf „Installierte Apps“ (oder „Heruntergeladene Apps“), dann auf „Clean – Apps sperren“ und schalte es ein.\n\n" +
            "Deine anderen Apps und Anmeldungen bleiben dabei unverändert auf dem Handy.\n\n" +
            "Ist der Schalter grau: Tippe unten auf „App-Info“, dann oben rechts auf ⋮ und auf „Eingeschränkte Einstellungen zulassen“.",
        { Guard.openGuardSettings(this) }
    )

    private fun dialog(title: String, msg: String, open: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("Öffnen") { _, _ -> open() }
            .setNeutralButton("App-Info") { _, _ -> Guard.openAppInfo(this) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun askPermissions() {
        val ask = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) ask += Manifest.permission.ACTIVITY_RECOGNITION
        requestPermissions(ask.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        Weather.refresh(this)
        showSetup()
    }

    companion object {
        fun permissionsDone(c: Context) =
            c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT < 29 ||
                    c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)

        /** Weather and steps are optional, so they don't keep the reminder on the home screen. */
        fun setupComplete(c: Context) =
            HomeActivity.isDefaultHome(c) && Guard.notifyEnabled(c) &&
                (Guard.appGuardEnabled(c) || Lockdown.isOwner(c))
    }
}
