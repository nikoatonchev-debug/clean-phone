package de.cleanphone.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Short guide shown when Google Play is not signed in yet. "Weiter" opens only the
 * Google sign-in (no store to browse). Right after signing in Clean takes over again
 * and loads the app the user wanted.
 */
class SignInActivity : Activity() {
    private lateinit var pal: Palette
    private var pkg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette.of(this)
        applySystemBars(pal)
        pkg = intent.getStringExtra("pkg")

        val frame = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(72), dp(28), dp(140))
        }
        val badge = FrameLayout(this).apply { background = roundRect(pal.card, dp(28).toFloat()) }
        badge.addView(ImageView(this).apply { setImageDrawable(GlyphIcon(Glyph.STORE, pal.text, tile = false)) },
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        col.addView(badge, LinearLayout.LayoutParams(dp(64), dp(64)))
        val title = text("Einmal bei Google\nanmelden", 32f, pal.text, weight = 700).apply {
            setPadding(0, dp(28), 0, dp(12))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        col.addView(title)
        val name = pkg?.let { p -> Catalog.apps.firstOrNull { it.pkg == p }?.name }
        col.addView(text(
            "Damit Clean ${name ?: "Apps"} für dich laden kann, braucht es einmalig dein Google-Konto. " +
                "Danach geht alles von allein – auch das Aktualisieren.",
            16f, pal.sub
        ).apply { setLineSpacing(dp(3).toFloat(), 1f) })

        val steps = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = roundRect(pal.card, dp(20).toFloat())
        }
        listOf(
            "Tippe unten auf „Weiter“",
            "Gib deine Gmail-Adresse und dein Passwort ein",
            "Fertig – Clean holt dich zurück und lädt die App",
        ).forEachIndexed { i, s ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            row.addView(text("${i + 1}", 14f, pal.onPrimary, weight = 600).apply {
                gravity = Gravity.CENTER
                background = roundRect(pal.primary, dp(13).toFloat())
            }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(14) })
            row.addView(text(s, 15f, pal.text), LinearLayout.LayoutParams(0, -2, 1f))
            steps.addView(row)
        }
        col.addView(steps, lp().apply { topMargin = dp(28) })
        frame.addView(col, FrameLayout.LayoutParams(-1, -2))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        buttons.addView(pill("Weiter", pal, primary = true) { signIn() }.apply {
            textSize = 16f
            setPadding(dp(56), dp(15), dp(56), dp(15))
            background = roundRect(pal.primary, dp(28).toFloat())
        }, LinearLayout.LayoutParams(-2, -2))
        buttons.addView(text("Später", 15f, pal.sub, weight = 500).apply {
            setPadding(dp(24), dp(16), dp(24), dp(8))
            softPress()
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-2, -2))
        frame.addView(buttons, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(40) })
        setContentView(frame)

        listOf<View>(badge, title, steps).forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = dp(14).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(60L + i * 90L).setDuration(500)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    /** Opens only Android's own "add Google account" screens. */
    private fun signIn() {
        val i = Intent(Settings.ACTION_ADD_ACCOUNT).putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
        runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(i, 5)
        }.onFailure {
            Toast.makeText(this, "Die Google-Anmeldung ließ sich nicht öffnen", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != 5) return
        if (resultCode == RESULT_CANCELED) return // stay on the guide, nothing happened
        // Signed in: load the app the user wanted, in the background.
        prefs().edit().putBoolean("play_signin_needed", false).apply()
        val p = pkg
        finish()
        if (p != null) Installer.install(applicationContext, p)
    }

    companion object {
        fun open(c: Context, pkg: String) {
            c.startActivity(Intent(c, SignInActivity::class.java).putExtra("pkg", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }
}
