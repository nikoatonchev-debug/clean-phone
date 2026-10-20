package de.cleanphone.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
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
    private lateinit var banner: TextView
    private lateinit var dateText: TextView
    private val dateFormat = java.text.SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY)
    private val tick = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { dateText.text = dateFormat.format(java.util.Date()) }
    }
    private var torchOn = false
    private var torchId: String? = null
    private var builtDark: Boolean? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == torchId) { torchOn = enabled; buildDock() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstRun()
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

    private fun firstRun() {
        val p = prefs()
        if (p.getBoolean("first_done", false)) return
        p.edit().putBoolean("first_done", true).apply()
        Wallpapers.apply(this, Wallpapers.presets.first()) { recreate() }
        val ask = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) ask += Manifest.permission.ACTIVITY_RECOGNITION
        requestPermissions(ask.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        refreshInfo(force = true)
    }

    private fun build() {
        pal = Palette.of(this)
        builtDark = pal.dark
        val fg = pal.text
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            (if (pal.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)

        val root = SwipeFrame(this) { openQuickSettings() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            col.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }

        // Clock, date and the small info line.
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(28))
        }
        head.addView(TextClock(this).apply {
            format12Hour = "HH:mm"; format24Hour = "HH:mm"
            textSize = 76f
            setTextColor(fg)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
            shadow(this)
        })
        dateText = text("", 16f, fg).apply { gravity = Gravity.CENTER; shadow(this) }
        head.addView(dateText)
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        info.addView(ImageView(this).apply { setImageDrawable(GlyphIcon(Glyph.STEPS, fg, tile = false)) },
            LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(6) })
        stepsText = text("–", 14f, fg).also { shadow(it) }
        info.addView(stepsText)
        info.addView(text("     ", 14f, fg))
        weatherIcon = ImageView(this)
        info.addView(weatherIcon, LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(6) })
        weatherText = text("", 14f, fg).also { shadow(it) }
        info.addView(weatherText)
        stepsText.setOnClickListener { open(StepsActivity::class.java) }
        weatherText.setOnClickListener { open(WeatherActivity::class.java) }
        weatherIcon.setOnClickListener { open(WeatherActivity::class.java) }
        head.addView(info)
        col.addView(head, lp())

        banner = text("Als Startbildschirm festlegen", 14f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = pressable(roundRect(Glyph.STORE.tileColor, dp(20).toFloat()), true)
            setOnClickListener { askDefaultHome() }
        }
        col.addView(banner, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16)
        })

        grid = GridLayout(this).apply { columnCount = 4 }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(grid, FrameLayout.LayoutParams(-1, -2))
        }
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            leftMargin = dp(20); rightMargin = dp(20)
        })

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = roundRect(if (pal.dark) 0x26FFFFFF else 0x40FFFFFF, dp(28).toFloat())
        }
        col.addView(dock, lp().apply { setMargins(dp(20), dp(8), dp(20), dp(16)) })
        setContentView(root)
    }

    private fun shadow(t: TextView) {
        t.setShadowLayer(dp(6).toFloat(), 0f, dp(1).toFloat(), if (pal.dark) 0x66000000 else 0x33FFFFFF)
    }

    override fun onResume() {
        super.onResume()
        if (builtDark != Palette.of(this).dark) build()
        Lockdown.apply(this)
        buildGrid()
        buildDock()
        banner.visibility = if (isDefaultHome() || Lockdown.isOwner(this)) View.GONE else View.VISIBLE
        refreshInfo(force = false)
        dateText.text = dateFormat.format(java.util.Date())
        registerReceiver(tick, android.content.IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(tick) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* home stays home */ }

    // ---------------------------------------------------------------- tiles

    private class Tile(val icon: Drawable, val label: String, val onClick: () -> Unit)

    private fun appTile(pkg: String?): Tile? {
        if (pkg == null || !Apps.launchable(this, pkg)) return null
        val icon = Apps.icon(this, pkg, prefs().getBoolean("mono", false)) ?: return null
        return Tile(icon, Apps.label(this, pkg)) { Apps.launch(this, pkg) }
    }

    private fun glyphTile(g: Glyph, label: String, onClick: () -> Unit): Tile {
        val mono = prefs().getBoolean("mono", false)
        return Tile(adaptive(if (mono) Color.parseColor("#3A3A38") else g.tileColor, g), label, onClick)
    }

    /** Our own icons get the phone's icon shape (squircle on Samsung), just like real app logos. */
    private fun adaptive(color: Int, g: Glyph): Drawable = GlyphIcon(g, tileColor = color, systemShape = true)

    private fun tiles(): List<Tile> {
        val core = listOf("com.whatsapp", "com.spotify.music", "com.google.android.apps.maps")
        val list = mutableListOf<Tile?>(
            appTile("com.whatsapp"),
            appTile("com.spotify.music"),
            appTile(Apps.camera(this)),
            appTile(Apps.gallery(this)),
            appTile("com.google.android.apps.maps"),
            appTile(Apps.clock(this)),
            glyphTile(Glyph.WEATHER, "Wetter") { open(WeatherActivity::class.java) },
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

    private fun tileView(t: Tile, size: Int): View = ImageView(this).apply {
        setImageDrawable(t.icon)
        contentDescription = t.label
        background = pressable(null, pal.dark)
        setOnClickListener { t.onClick() }
        setPadding(dp(2), dp(2), dp(2), dp(2))
        layoutParams = LinearLayout.LayoutParams(size, size)
    }

    private fun buildGrid() {
        grid.removeAllViews()
        val cell = (resources.displayMetrics.widthPixels - dp(40)) / 4
        val icon = minOf(dp(60), cell - dp(16))
        for (t in tiles()) {
            val holder = FrameLayout(this)
            holder.addView(tileView(t, icon), FrameLayout.LayoutParams(icon, icon, Gravity.CENTER))
            grid.addView(holder, GridLayout.LayoutParams().apply {
                width = cell; height = icon + dp(26)
            })
        }
    }

    private fun buildDock() {
        if (!::dock.isInitialized) return
        dock.removeAllViews()
        val size = dp(54)
        val items = listOfNotNull(
            appTile(Apps.dialer(this)),
            appTile(Apps.sms(this)),
            appTile(Apps.contacts(this)),
            if (torchId != null) Tile(
                adaptive(if (torchOn) Color.parseColor("#EF9F27") else Glyph.TORCH.tileColor, Glyph.TORCH),
                "Taschenlampe"
            ) { toggleTorch() } else null
        )
        for (t in items) {
            dock.addView(tileView(t, size), LinearLayout.LayoutParams(0, size, 1f))
        }
    }

    private fun toggleTorch() {
        val id = torchId ?: return
        runCatching { getSystemService(CameraManager::class.java).setTorchMode(id, !torchOn) }
    }

    // ---------------------------------------------------------------- info

    private fun refreshInfo(force: Boolean) {
        stepsText.text = "%,d Schritte".format(Locale.GERMANY, StepTracker.today(this))
        StepTracker.read(this) { steps -> stepsText.text = "%,d Schritte".format(Locale.GERMANY, steps) }
        showWeather()
        if (force || Weather.stale(this)) Weather.refresh(this) { showWeather() }
    }

    private fun showWeather() {
        val t = Weather.currentTemp(this)
        weatherText.text = t?.let { "$it ${Weather.describe(Weather.currentCode(this))}" } ?: "Wetter"
        weatherIcon.setImageDrawable(GlyphIcon(Weather.glyph(Weather.currentCode(this)), pal.text, tile = false))
    }

    // ---------------------------------------------------------------- system

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    @SuppressLint("WrongConstant")
    private fun openQuickSettings() {
        android.util.Log.i("Clean", "pull down")
        runCatching {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(sb)
        }.onFailure { android.util.Log.w("Clean", "quick settings", it) }
    }

    private fun isDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

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
}
