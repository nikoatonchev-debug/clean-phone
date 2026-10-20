package de.cleanphone.launcher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Real logos for apps that are not installed yet, fetched once from Google Play and kept on the phone. */
object StoreIcons {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val memory = HashMap<String, Bitmap>()

    private fun file(c: Context, pkg: String) = File(c.cacheDir, "icon_$pkg.png")

    fun load(c: Context, pkg: String, into: ImageView) {
        into.tag = pkg
        memory[pkg]?.let { into.setImageBitmap(it); return }
        val app = c.applicationContext
        pool.execute {
            val f = file(app, pkg)
            val bmp = runCatching {
                if (!f.exists()) download(pkg, f)
                BitmapFactory.decodeFile(f.path)
            }.getOrNull() ?: return@execute
            memory[pkg] = bmp
            main.post { if (into.tag == pkg) { into.alpha = 0f; into.setImageBitmap(bmp); into.animate().alpha(1f).setDuration(200).start() } }
        }
    }

    private fun get(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000; readTimeout = 8000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
    }

    private fun download(pkg: String, to: File) {
        val html = get("https://play.google.com/store/apps/details?id=$pkg&hl=de").inputStream.bufferedReader().use { it.readText() }
        val img = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: return
        val bytes = get(img.substringBefore('=') + "=s144-rw").inputStream.use { it.readBytes() }
        to.writeBytes(bytes)
    }
}

/**
 * App Store with a fixed, hand-picked list and a search field. Installing goes
 * through Google Play, but only the apps listed here can be reached from this screen.
 */
class StoreActivity : ScreenActivity() {
    override val screenTitle = "App Store"
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            background = roundRect(pal.card, dp(14).toFloat())
        }
        box.addView(ImageView(this).apply {
            setImageDrawable(GlyphIcon(Glyph.SEARCH, pal.sub, tile = false))
        }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(10) })
        search = EditText(this).apply {
            hint = "Apps suchen"
            setHintTextColor(pal.sub)
            setTextColor(pal.text)
            textSize = 16f
            typeface = Fonts.get(this@StoreActivity, 400)
            background = null
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(0, dp(12), 0, dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim().orEmpty()
                    render()
                }
            })
            setOnEditorActionListener { v, _, _ ->
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(v.windowToken, 0); true
            }
        }
        box.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(box, lp().apply { bottomMargin = dp(6) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list, lp())
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun matches(app: StoreApp): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return app.name.lowercase().contains(q) || app.info.lowercase().contains(q) || app.category.lowercase().contains(q)
    }

    private fun render() {
        list.removeAllViews()
        val shown = Catalog.apps.filter { matches(it) }
        if (shown.isEmpty()) {
            list.addView(text("Keine passende App.\nHier gibt es nur nützliche Apps – nichts zum Hängenbleiben.", 15f, pal.sub).apply {
                gravity = Gravity.CENTER
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(dp(10), dp(48), dp(10), 0)
            }, lp())
            return
        }
        val groups = if (query.isEmpty()) shown.groupBy { it.category } else mapOf("Ergebnisse" to shown)
        for ((cat, apps) in groups) {
            list.addView(text(cat.uppercase(), 12f, pal.sub, weight = 500).apply {
                letterSpacing = 0.06f
                setPadding(dp(6), dp(24), 0, dp(10))
            })
            val group = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundRect(pal.card, dp(20).toFloat())
            }
            apps.forEachIndexed { i, app ->
                if (i > 0) group.addView(View(this).apply { setBackgroundColor(pal.line) },
                    LinearLayout.LayoutParams(-1, 1).apply { leftMargin = dp(82) })
                group.addView(appRow(app), lp())
            }
            list.addView(group, lp())
        }
    }

    private fun appRow(app: StoreApp): View {
        val installed = Apps.launchable(this, app.pkg)
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        // Icon: the real logo (installed or from Google Play), a soft letter until it has loaded.
        val iconBox = FrameLayout(this).apply { background = roundRect(pal.line, dp(14).toFloat()) }
        val letter = text(app.name.first().toString(), 20f, pal.sub, weight = 600).apply { gravity = Gravity.CENTER }
        iconBox.addView(letter, FrameLayout.LayoutParams(-1, -1))
        val icon = ImageView(this).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, dp(14).toFloat())
                }
            }
        }
        iconBox.addView(icon, FrameLayout.LayoutParams(-1, -1))
        val real = if (installed) Apps.icon(this, app.pkg, mono = false) else null
        if (real != null) { icon.setImageDrawable(real); iconBox.background = null; letter.visibility = View.GONE }
        else StoreIcons.load(this, app.pkg, icon)
        r.addView(iconBox, LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(16) })

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(app.name, 16f, pal.text, weight = 600))
        texts.addView(text(app.info, 13f, pal.sub).apply { setPadding(0, dp(3), 0, 0) })
        r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        r.addView(pill(if (installed) "Öffnen" else "Laden", pal, primary = !installed) {
            if (installed) Apps.launch(this, app.pkg) else Apps.openPlayStore(this, app)
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) })

        if (installed) {
            r.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setMessage("${app.name} vom Handy entfernen?")
                    .setPositiveButton("Entfernen") { _, _ ->
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)))
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
                true
            }
        }
        return r
    }
}
