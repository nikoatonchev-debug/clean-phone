package de.cleanphone.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import java.io.ByteArrayInputStream

/** Sites that may never open, plus the ones the user adds. Subdomains are blocked too. */
object Blocklist {
    val defaults = listOf(
        // Video and social media
        "youtube.com", "youtu.be", "youtube-nocookie.com", "tiktok.com", "instagram.com", "facebook.com",
        "fb.com", "fb.watch", "x.com", "twitter.com", "snapchat.com", "reddit.com", "pinterest.com",
        "tumblr.com", "threads.net", "9gag.com", "twitch.tv", "kick.com", "vimeo.com", "dailymotion.com",
        "netflix.com", "primevideo.com", "disneyplus.com", "joyn.de", "rtlplus.de",
        // Shopping rabbit holes
        "amazon.de", "amazon.com", "ebay.de", "ebay.com", "temu.com", "shein.com", "aliexpress.com", "wish.com",
        // Games and gambling
        "poki.com", "crazygames.com", "miniclip.com", "roblox.com", "tipico.de", "bet365.com", "bwin.com",
        "pokerstars.com", "lottoland.com",
        // Adult
        "pornhub.com", "xvideos.com", "xhamster.com", "xnxx.com", "youporn.com", "onlyfans.com",
    )

    fun custom(c: Context): Set<String> = c.prefs().getStringSet("blocked_custom", emptySet())!!

    fun add(c: Context, domain: String) {
        val clean = domain.trim().lowercase().removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").substringBefore('/')
        if (clean.isEmpty()) return
        c.prefs().edit().putStringSet("blocked_custom", custom(c) + clean).apply()
    }

    fun remove(c: Context, domain: String) {
        c.prefs().edit().putStringSet("blocked_custom", custom(c) - domain).apply()
    }

    fun isBlocked(c: Context, url: String?): Boolean {
        val host = url?.let { runCatching { Uri.parse(it).host }.getOrNull() }?.lowercase() ?: return false
        return (defaults + custom(c)).any { host == it || host.endsWith(".$it") }
    }
}

/**
 * The only web browser on the phone. There is no address bar and no search:
 * pages are opened from QR codes or links in other apps, and blocked sites are refused.
 */
class BrowserActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var host: android.widget.TextView
    private lateinit var blocked: LinearLayout
    private lateinit var blockedText: android.widget.TextView
    private lateinit var pal: Palette

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette.of(this)
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (pal.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pal.bg) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(6), dp(8))
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundRect(pal.card, dp(12).toFloat())
        }
        pill.addView(ImageView(this).apply {
            setImageDrawable(GlyphIcon(Glyph.LOCK, pal.sub, tile = false))
        }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(8) })
        host = text("", 14f, pal.text).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
        pill.addView(host, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(pill, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(ImageView(this).apply {
            setImageDrawable(GlyphIcon(Glyph.CLOSE, pal.text, tile = false))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = pressable(null, pal.dark)
            contentDescription = "Schließen"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(bar, lp())

        val frame = FrameLayout(this)
        web = WebView(this)
        frame.addView(web, FrameLayout.LayoutParams(-1, -1))

        blocked = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(pal.bg)
            setPadding(dp(40), 0, dp(40), dp(60))
            visibility = View.GONE
        }
        blocked.addView(ImageView(this).apply {
            setImageDrawable(GlyphIcon(Glyph.BLOCK, pal.text, tile = false))
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        blocked.addView(text("Gesperrt", 24f, pal.text, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(8)) })
        blockedText = text("", 15f, pal.sub).apply { gravity = Gravity.CENTER }
        blocked.addView(blockedText)
        frame.addView(blocked, FrameLayout.LayoutParams(-1, -1))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        web.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(this, "Herunterladen ist hier nicht möglich", Toast.LENGTH_SHORT).show()
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = handle(req.url.toString())

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (Blocklist.isBlocked(this@BrowserActivity, url)) {
                    view.stopLoading(); showBlocked(url)
                } else {
                    host.text = Uri.parse(url).host?.removePrefix("www.") ?: url
                }
            }

            // Also stops embedded videos (e.g. a YouTube player inside a restaurant page).
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (Blocklist.isBlocked(this@BrowserActivity, req.url.toString())) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
        }
        open(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    private fun open(i: Intent?) {
        val url = i?.dataString ?: return finish()
        if (Blocklist.isBlocked(this, url)) showBlocked(url)
        else {
            blocked.visibility = View.GONE
            web.loadUrl(url)
        }
    }

    /** Returns true when the WebView must NOT load the url itself. */
    private fun handle(url: String): Boolean {
        val uri = Uri.parse(url)
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                if (Blocklist.isBlocked(this, url)) { showBlocked(url); true } else false
            }
            "tel", "mailto", "geo", "sms", "smsto", "whatsapp" -> {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                true
            }
            "intent" -> {
                val fallback = runCatching {
                    Intent.parseUri(url, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                }.getOrNull()
                if (fallback != null && !Blocklist.isBlocked(this, fallback)) web.loadUrl(fallback)
                true
            }
            else -> true
        }
    }

    private fun showBlocked(url: String) {
        val h = Uri.parse(url).host?.removePrefix("www.") ?: url
        host.text = h
        blockedText.text = "$h ist auf diesem Handy nicht erlaubt."
        blocked.visibility = View.VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (blocked.visibility == View.VISIBLE && web.canGoBack()) {
            blocked.visibility = View.GONE
            host.text = Uri.parse(web.url ?: "").host?.removePrefix("www.") ?: ""
        } else if (web.canGoBack()) web.goBack()
        else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}

class BlockedSitesActivity : ScreenActivity() {
    override val screenTitle = "Gesperrte Seiten"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        content.removeAllViews()
        content.addView(text(
            "Über einen QR-Code kommst du auf jede normale Internetseite, zum Beispiel eine Speisekarte. Diese Seiten bleiben aber immer gesperrt.",
            15f, pal.sub
        ).apply { setPadding(dp(4), 0, dp(4), dp(8)) })

        row("Seite hinzufügen", Glyph.PLUS) { askAdd() }

        val custom = Blocklist.custom(this).sorted()
        if (custom.isNotEmpty()) {
            section("Von dir hinzugefügt (antippen zum Entfernen)")
            for (d in custom) row(d, Glyph.BLOCK) {
                AlertDialog.Builder(this)
                    .setMessage("$d wieder erlauben?")
                    .setPositiveButton("Erlauben") { _, _ -> Blocklist.remove(this, d); render() }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }

        section("Immer gesperrt")
        content.addView(text(Blocklist.defaults.joinToString("\n"), 15f, pal.text).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundRect(pal.card, dp(14).toFloat())
            setLineSpacing(dp(4).toFloat(), 1f)
        }, lp())
    }

    private fun askAdd() {
        val input = EditText(this).apply { hint = "z. B. spiele-seite.de"; setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle("Seite sperren")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input, lp()) })
            .setPositiveButton("Sperren") { _, _ -> Blocklist.add(this, input.text.toString()); render() }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
