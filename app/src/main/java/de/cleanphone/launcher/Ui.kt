package de.cleanphone.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared preferences used by every screen. */
fun Context.prefs() = getSharedPreferences("clean", Context.MODE_PRIVATE)

fun Context.dp(v: Number): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

/** Inter (bundled), so Clean looks the same on every phone. */
object Fonts {
    private val cache = HashMap<Int, Typeface>()

    fun get(c: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        runCatching {
            Typeface.Builder(c.assets, "inter.ttf").setFontVariationSettings("'wght' $weight").build()
        }.getOrNull() ?: Typeface.DEFAULT
    }
}

/** Colors for the current light/dark choice. Light is the default. */
class Palette(val dark: Boolean) {
    val bg = if (dark) Color.parseColor("#0B0B0C") else Color.WHITE
    val card = if (dark) Color.parseColor("#1A1A1C") else Color.parseColor("#F5F5F4")
    val text = if (dark) Color.parseColor("#F5F5F5") else Color.parseColor("#111111")
    val sub = if (dark) Color.parseColor("#8E8E93") else Color.parseColor("#8A8A8E")
    val line = if (dark) Color.parseColor("#2A2A2D") else Color.parseColor("#E8E8E6")
    val danger = Color.parseColor("#E5484D")
    val accent = if (dark) Color.parseColor("#7DB4FF") else Color.parseColor("#2563EB")

    /** Main buttons are simply black (or white in dark mode). */
    val primary = text
    val onPrimary = bg

    companion object {
        fun of(c: Context) = Palette(c.prefs().getBoolean("dark", false))
    }
}

fun roundRect(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

fun Context.pressable(base: Drawable?, dark: Boolean): Drawable =
    RippleDrawable(
        ColorStateList.valueOf(if (dark) 0x22FFFFFF else 0x14000000),
        base, base ?: ColorDrawable(Color.WHITE)
    )

/** Gentle shrink while a finger is on the view - the "smooth" feel everywhere in Clean. */
@SuppressLint("ClickableViewAccessibility")
fun View.softPress(scale: Float = 0.96f) {
    // No gray keyboard-focus box on touch screens.
    defaultFocusHighlightEnabled = false
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2f)).start()
        }
        false
    }
}

fun Context.text(
    s: CharSequence,
    size: Float,
    color: Int,
    bold: Boolean = false,
    light: Boolean = false,
    weight: Int = if (bold) 600 else if (light) 300 else 400
) = TextView(this).apply {
    text = s
    textSize = size
    setTextColor(color)
    typeface = Fonts.get(this@text, weight)
    includeFontPadding = false
    if (size >= 28f) letterSpacing = -0.02f
}

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
    LinearLayout.LayoutParams(w, h)

/** Rounded pill button. Primary = filled black, otherwise soft gray. */
fun Context.pill(label: String, pal: Palette, primary: Boolean, onClick: () -> Unit): TextView =
    text(label, 14f, if (primary) pal.onPrimary else pal.text, weight = 600).apply {
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(9), dp(18), dp(9))
        background = roundRect(if (primary) pal.primary else pal.card, dp(20).toFloat())
        softPress(0.94f)
        setOnClickListener { onClick() }
    }

fun Activity.applySystemBars(pal: Palette) {
    window.statusBarColor = pal.bg
    window.navigationBarColor = pal.bg
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility = if (pal.dark) 0 else
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
}

/**
 * Base for every inner screen: plain background, a round back button, a large
 * title and a scrolling column [content]. Rows are collected into rounded groups.
 */
abstract class ScreenActivity : Activity() {
    lateinit var pal: Palette
    lateinit var content: LinearLayout
    lateinit var root: LinearLayout
    private var group: LinearLayout? = null

    abstract val screenTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette.of(this)
        window.decorView.setBackgroundColor(pal.bg)
        applySystemBars(pal)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
        }
        val back = ImageView(this).apply {
            setImageDrawable(GlyphIcon(Glyph.BACK, pal.text, tile = false))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = roundRect(pal.card, dp(20).toFloat())
            softPress(0.9f)
            setOnClickListener { finish() }
            contentDescription = "Zurück"
        }
        root.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            leftMargin = dp(20); topMargin = dp(14)
        })
        root.addView(text(screenTitle, 34f, pal.text, weight = 700).apply {
            setPadding(dp(24), dp(18), dp(24), dp(6))
        })

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(40))
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    fun section(title: String) {
        group = null
        content.addView(text(title.uppercase(), 12f, pal.sub, weight = 500).apply {
            letterSpacing = 0.06f
            setPadding(dp(6), dp(26), 0, dp(10))
        })
    }

    private fun currentGroup(): LinearLayout {
        val g = group
        if (g != null && content.childCount > 0 && content.getChildAt(content.childCount - 1) === g) return g
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(pal.card, dp(20).toFloat())
            clipToOutline = true
            content.addView(this, lp().apply { bottomMargin = dp(4) })
            group = this
        }
    }

    /** A tappable row inside a rounded group. Returns the value label so callers can update it. */
    fun row(
        title: String,
        glyph: Glyph? = null,
        value: String? = null,
        color: Int = pal.text,
        chevron: Boolean = true,
        onClick: () -> Unit
    ): TextView {
        val g = currentGroup()
        if (g.childCount > 0) {
            g.addView(View(this).apply { setBackgroundColor(pal.line) },
                LinearLayout.LayoutParams(-1, 1).apply { leftMargin = dp(if (glyph != null) 56 else 18) })
        }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54)
            setPadding(dp(18), dp(14), dp(16), dp(14))
            background = pressable(null, pal.dark)
            setOnClickListener { onClick() }
        }
        if (glyph != null) {
            r.addView(ImageView(this).apply {
                setImageDrawable(GlyphIcon(glyph, color, tile = false))
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(18) })
        }
        r.addView(text(title, 16f, color), LinearLayout.LayoutParams(0, -2, 1f))
        val v = text(value ?: "", 15f, pal.sub)
        r.addView(v)
        if (chevron) {
            r.addView(ImageView(this).apply {
                setImageDrawable(GlyphIcon(Glyph.CHEVRON, pal.line, tile = false))
            }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { leftMargin = dp(8) })
        }
        g.addView(r, lp())
        return v
    }

    /** Plain explanatory text between groups. */
    fun note(s: String) {
        group = null
        content.addView(text(s, 14f, pal.sub).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(6), dp(4), dp(6), dp(12))
        })
    }
}
