package de.cleanphone.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Pull down anywhere on the home screen to open the quick settings (WLAN, energy saving, torch, ...). */
class SwipeFrame(c: Context, private val onPullDown: () -> Unit) : FrameLayout(c) {
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val slop = ViewConfiguration.get(c).scaledTouchSlop * 3

    // Watches every touch (the scrolling app grid would otherwise swallow the gesture).
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; fired = false }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - downY
                if (!fired && dy > slop && dy > abs(e.x - downX) * 1.5f) {
                    fired = true
                    val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    onPullDown()
                }
            }
        }
        if (fired) return true
        return super.dispatchTouchEvent(e)
    }
}

class HomeActivity : Activity() {
    private lateinit var pal: Palette
    private lateinit var grid: GridLayout
    private lateinit var dock: LinearLayout
    private lateinit var stepsText: TextView
    private lateinit var weatherText: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var setupHint: TextView
    private lateinit var dateText: TextView
    private var torchOn = false
    private var torchId: String? = null
    private var builtDark: Boolean? = null
    private var animateIn = true

    private val dateFormat = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY)
    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { dateText.text = dateFormat.format(Date()) }
    }
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == torchId) { torchOn = enabled; buildDock() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!prefs().getBoolean("first_done", false)) {
            prefs().edit().putBoolean("first_done", true).apply()
            Wallpapers.apply(this, Wallpapers.presets.first())
            startActivity(Intent(this, WelcomeActivity::class.java))
        }
        build()
        val cm = getSystemService(CameraManager::class.java)
        torchId = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
        cm.registerTorchCallback(torchCallback, null)
    }

    override fun onDestroy() {
        getSystemService(CameraManager::class.java).unregisterTorchCallback(torchCallback)
        super.onDestroy()
    }

    /** White glass in light mode, smoked glass in dark mode. */
    private fun tileBg(radius: Int) =
        roundRect(if (pal.dark) 0x1FFFFFFF else 0xF2FFFFFF.toInt(), dp(radius).toFloat(), 1,
            if (pal.dark) 0x1AFFFFFF else 0x0F000000)

    private fun build() {
        pal = Palette.of(this)
        builtDark = pal.dark
        animateIn = true
        val fg = pal.text
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            (if (pal.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)

        val root = SwipeFrame(this) { openQuickSettings() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            col.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }

        // Clock, date and two small info chips - left aligned, lots of air.
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(30), dp(44), dp(30), dp(30))
        }
        head.addView(TextClock(this).apply {
            format12Hour = "HH:mm"; format24Hour = "HH:mm"
            textSize = 84f
            setTextColor(fg)
            typeface = Fonts.get(this@HomeActivity, 200)
            letterSpacing = -0.04f
            includeFontPadding = false
        })
        dateText = text("", 18f, fg, weight = 500).apply { setPadding(dp(4), dp(6), 0, 0) }
        head.addView(dateText)

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(20), 0, 0)
        }
        fun chip(glyphView: ImageView, label: TextView, onClick: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(14), dp(8))
            background = tileBg(18)
            addView(glyphView, LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(7) })
            addView(label)
            softPress()
            setOnClickListener { onClick() }
        }
        stepsText = text("–", 14f, fg, weight = 500)
        chips.addView(chip(ImageView(this).apply { setImageDrawable(GlyphIcon(Glyph.STEPS, fg, tile = false)) }, stepsText) {
            open(StepsActivity::class.java)
        })
        weatherIcon = ImageView(this)
        weatherText = text("Wetter", 14f, fg, weight = 500)
        chips.addView(chip(weatherIcon, weatherText) { open(WeatherActivity::class.java) },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        head.addView(chips)

        setupHint = text("Einrichtung abschließen  ›", 14f, pal.onPrimary, weight = 600).apply {
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = roundRect(pal.primary, dp(18).toFloat())
            softPress()
            setOnClickListener {
                startActivity(Intent(this@HomeActivity, WelcomeActivity::class.java).putExtra("setup", true))
            }
        }
        head.addView(setupHint, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
        col.addView(head, lp())

        grid = GridLayout(this).apply { columnCount = 4 }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(4))
            addView(grid, FrameLayout.LayoutParams(-1, -2))
        }
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            leftMargin = dp(18); rightMargin = dp(18)
        })

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = tileBg(32)
            elevation = dp(2).toFloat()
        }
        col.addView(dock, lp().apply { setMargins(dp(22), dp(8), dp(22), dp(18)) })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (builtDark != Palette.of(this).dark) { build(); dirty = true }
        Guard.enableSilently(this)
        Lockdown.apply(this)
        // The grid is only rebuilt when apps or the look changed, so coming home stays instant.
        if (dirty || grid.childCount == 0) {
            dirty = false
            Guard.invalidate()
            buildGrid()
            buildDock()
        }
        setupHint.visibility = if (WelcomeActivity.setupComplete(this)) View.GONE else View.VISIBLE
        refreshInfo(force = false)
        dateText.text = dateFormat.format(Date())
        registerReceiver(tick, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(tick) }
        animateIn = false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* home stays home */ }

    // ---------------------------------------------------------------- tiles

    private class Tile(val icon: Drawable, val label: String, val isGlyph: Boolean, val onClick: () -> Unit)

    private fun appTile(pkg: String?): Tile? {
        if (pkg == null || !Apps.launchable(this, pkg)) return null
        val icon = Apps.icon(this, pkg, prefs().getBoolean("mono", false)) ?: return null
        return Tile(icon, Apps.label(this, pkg), false) { Apps.launch(this, pkg) }
    }

    /** Clean's own apps are drawn as simple line icons, so they feel like part of the system. */
    private fun glyphTile(g: Glyph, label: String, onClick: () -> Unit) =
        Tile(GlyphIcon(g, pal.text, tile = false), label, true, onClick)

    private fun tiles(): List<Tile> {
        val core = listOf("com.whatsapp", "com.spotify.music", "com.google.android.apps.maps")
        val list = mutableListOf<Tile?>(
            appTile("com.whatsapp"),
            appTile("com.spotify.music"),
            appTile(Apps.camera(this)),
            appTile(Apps.gallery(this)),
            appTile("com.google.android.apps.maps"),
            appTile(Apps.clock(this)),
            glyphTile(Glyph.SUN, "Wetter") { open(WeatherActivity::class.java) },
            glyphTile(Glyph.STEPS, "Schritte") { open(StepsActivity::class.java) },
            glyphTile(Glyph.QR, "QR-Scanner") { open(ScanActivity::class.java) },
            appTile(Apps.calculator(this)),
            appTile(Apps.notes(this)),
        )
        // Apps the user added from the App Store.
        for (app in Catalog.apps) if (app.pkg !in core) list += appTile(app.pkg)
        list += glyphTile(Glyph.STORE, "App Store") { open(StoreActivity::class.java) }
        list += glyphTile(Glyph.SETTINGS, "Einstellungen") { open(SettingsActivity::class.java) }
        return list.filterNotNull().distinctBy { it.label }
    }

    private fun tileView(t: Tile, box: Int, framed: Boolean): View {
        val frame = FrameLayout(this).apply {
            if (framed) {
                background = tileBg(22)
                elevation = dp(1).toFloat()
            }
            contentDescription = t.label
            softPress(0.9f)
            setOnClickListener { t.onClick() }
        }
        val inner = when {
            t.isGlyph -> (box * 0.42f).toInt()
            framed -> (box * 0.7f).toInt()
            else -> (box * 0.86f).toInt()
        }
        frame.addView(ImageView(this).apply { setImageDrawable(t.icon) },
            FrameLayout.LayoutParams(inner, inner, Gravity.CENTER))
        return frame
    }

    private fun buildGrid() {
        grid.removeAllViews()
        val cell = (resources.displayMetrics.widthPixels - dp(36)) / 4
        val box = minOf(dp(66), cell - dp(14))
        tiles().forEachIndexed { i, t ->
            val holder = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
            val v = tileView(t, box, framed = true)
            holder.addView(v, FrameLayout.LayoutParams(box, box, Gravity.CENTER))
            grid.addView(holder, GridLayout.LayoutParams().apply { width = cell; height = box + dp(20) })
            if (animateIn) {
                v.alpha = 0f; v.translationY = dp(14).toFloat()
                v.animate().alpha(1f).translationY(0f).setStartDelay(40L + i * 22L).setDuration(380)
                    .setInterpolator(DecelerateInterpolator(2f)).start()
            }
        }
        animateIn = false
    }

    private fun buildDock() {
        if (!::dock.isInitialized) return
        dock.removeAllViews()
        val size = dp(52)
        val items = listOfNotNull(
            appTile(Apps.dialer(this)),
            appTile(Apps.sms(this)),
            appTile(Apps.contacts(this)),
            if (torchId != null) Tile(
                GlyphIcon(Glyph.TORCH, if (torchOn) Color.parseColor("#F5A524") else pal.text, tile = false),
                "Taschenlampe", true
            ) { toggleTorch() } else null
        )
        for (t in items) {
            val holder = FrameLayout(this)
            holder.addView(tileView(t, size, framed = false), FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            dock.addView(holder, LinearLayout.LayoutParams(0, size, 1f))
        }
    }

    private fun toggleTorch() {
        val id = torchId ?: return
        runCatching { getSystemService(CameraManager::class.java).setTorchMode(id, !torchOn) }
    }

    // ---------------------------------------------------------------- info

    private fun refreshInfo(force: Boolean) {
        stepsText.text = "%,d".format(Locale.GERMANY, StepTracker.today(this))
        StepTracker.read(this) { steps -> stepsText.text = "%,d".format(Locale.GERMANY, steps) }
        showWeather()
        if (force || Weather.stale(this)) Weather.refresh(this) { showWeather() }
    }

    private fun showWeather() {
        val t = Weather.currentTemp(this)
        weatherText.text = t?.let { "$it  ${Weather.describe(Weather.currentCode(this))}" } ?: "Wetter"
        weatherIcon.setImageDrawable(GlyphIcon(Weather.glyph(Weather.currentCode(this)), pal.text, tile = false))
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        refreshInfo(force = true)
    }

    // ---------------------------------------------------------------- system

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    @SuppressLint("WrongConstant")
    private fun openQuickSettings() {
        runCatching {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(sb)
        }
    }

    companion object {
        /** Set when apps are installed/removed or the look changes. */
        @Volatile var dirty = true

        fun isDefaultHome(c: Context): Boolean {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            return c.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == c.packageName
        }
    }
}
