package de.cleanphone.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared preferences used by every screen. */
fun Context.prefs() = getSharedPreferences("clean", Context.MODE_PRIVATE)

fun Context.dp(v: Number): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

/** Colors for the current light/dark choice. */
class Palette(val dark: Boolean) {
    val bg = if (dark) Color.parseColor("#0B1220") else Color.parseColor("#F6F5F1")
    val card = if (dark) Color.parseColor("#16203A") else Color.WHITE
    val text = if (dark) Color.WHITE else Color.parseColor("#14161C")
    val sub = if (dark) Color.parseColor("#9AA3B5") else Color.parseColor("#6B6E76")
    val line = if (dark) Color.parseColor("#26304A") else Color.parseColor("#E4E2DC")
    val danger = if (dark) Color.parseColor("#F09595") else Color.parseColor("#C0392B")
    val accent = if (dark) Color.parseColor("#85B7EB") else Color.parseColor("#185FA5")

    companion object {
        fun of(c: Context) = Palette(c.prefs().getBoolean("dark", true))
    }
}

fun roundRect(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

fun Context.pressable(base: Drawable?, dark: Boolean): Drawable =
    RippleDrawable(
        ColorStateList.valueOf(if (dark) 0x33FFFFFF else 0x22000000),
        base, ColorDrawable(Color.WHITE)
    )

fun Context.text(s: CharSequence, size: Float, color: Int, bold: Boolean = false, light: Boolean = false) =
    TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = when {
            bold -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            light -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
    LinearLayout.LayoutParams(w, h)

/**
 * Base for every inner screen: solid background, a header with back arrow and
 * title, and a scrolling column [content] to fill.
 */
abstract class ScreenActivity : Activity() {
    lateinit var pal: Palette
    lateinit var content: LinearLayout
    lateinit var root: LinearLayout

    abstract val screenTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette.of(this)
        window.decorView.setBackgroundColor(pal.bg)
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (pal.dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(12), dp(16), dp(8))
        }
        val back = ImageView(this).apply {
            setImageDrawable(GlyphIcon(Glyph.BACK, pal.text, tile = false))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = pressable(null, pal.dark)
            setOnClickListener { finish() }
            contentDescription = "Zurück"
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(text(screenTitle, 22f, pal.text, bold = true).apply {
            setPadding(dp(6), 0, 0, 0)
        })
        root.addView(header, lp())

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    fun section(title: String) {
        content.addView(text(title, 13f, pal.sub, bold = true).apply {
            setPadding(dp(4), dp(20), 0, dp(8))
        })
    }

    /** A tappable settings-style row. Returns the value label so callers can update it. */
    fun row(
        title: String,
        glyph: Glyph? = null,
        value: String? = null,
        color: Int = pal.text,
        onClick: () -> Unit
    ): TextView {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = pressable(roundRect(pal.card, dp(14).toFloat()), pal.dark)
            setOnClickListener { onClick() }
        }
        if (glyph != null) {
            r.addView(ImageView(this).apply {
                setImageDrawable(GlyphIcon(glyph, color, tile = false))
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(14) })
        }
        r.addView(text(title, 16f, color), LinearLayout.LayoutParams(0, -2, 1f))
        val v = text(value ?: "", 14f, pal.sub)
        r.addView(v)
        content.addView(r, lp().apply { bottomMargin = dp(8) })
        return v
    }
}
