package de.cleanphone.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * App Store with a fixed, hand-picked list. Installing goes through Google Play,
 * but only the apps listed here can be reached from this screen.
 */
class StoreActivity : ScreenActivity() {
    override val screenTitle = "App Store"

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        content.addView(text(
            "Nur nützliche Apps – keine Feeds, keine Kurzvideos, keine Spiele.",
            15f, pal.sub
        ).apply { setPadding(dp(4), 0, dp(4), dp(4)) })

        for ((cat, apps) in Catalog.apps.groupBy { it.category }) {
            section(cat)
            for (app in apps) addApp(app)
        }
    }

    private fun addApp(app: StoreApp) {
        val installed = Apps.launchable(this, app.pkg)
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundRect(pal.card, dp(14).toFloat())
        }
        val icon = ImageView(this)
        val real = if (installed) Apps.icon(this, app.pkg, mono = false) else null
        if (real != null) icon.setImageDrawable(real)
        r.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(14) })
        if (real == null) {
            // Not installed yet: a calm letter tile until Google Play delivers the real logo.
            r.removeView(icon)
            r.addView(TextView(this).apply {
                text = app.name.first().toString()
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(pal.text)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = GradientDrawable().apply { setColor(pal.line); cornerRadius = dp(13).toFloat() }
            }, 0, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(14) })
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(app.name, 16f, pal.text))
        texts.addView(text(app.info, 13f, pal.sub))
        r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        val button = text(if (installed) "Öffnen" else "Laden", 14f, if (installed) pal.text else Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = pressable(
                roundRect(if (installed) pal.line else Glyph.STORE.tileColor, dp(18).toFloat()), pal.dark
            )
            setOnClickListener {
                if (installed) Apps.launch(this@StoreActivity, app.pkg) else Apps.openPlayStore(this@StoreActivity, app)
            }
        }
        r.addView(button)

        if (installed) {
            r.setOnLongClickListener {
                android.app.AlertDialog.Builder(this)
                    .setMessage("${app.name} vom Handy entfernen?")
                    .setPositiveButton("Entfernen") { _, _ ->
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)))
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
                true
            }
        }
        content.addView(r, lp().apply { bottomMargin = dp(8) })
    }
}
