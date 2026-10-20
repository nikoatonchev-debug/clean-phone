package de.cleanphone.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/** Full-screen QR scanner. Web links open in the filtered browser, nothing else. */
class ScanActivity : Activity() {
    private lateinit var scanner: DecoratedBarcodeView
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        scanner = DecoratedBarcodeView(this).apply {
            barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            setStatusText("Halte die Kamera auf einen QR-Code")
        }
        root.addView(scanner, FrameLayout.LayoutParams(-1, -1))
        root.addView(ImageView(this).apply {
            setImageDrawable(GlyphIcon(Glyph.CLOSE, Color.WHITE, tile = false))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundRect(0x66000000, dp(24).toFloat())
            contentDescription = "Schließen"
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(36); rightMargin = dp(16)
        })
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (res.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Ohne Kamera-Erlaubnis kann nichts gescannt werden", Toast.LENGTH_LONG).show()
            finish()
        } else start()
    }

    override fun onResume() {
        super.onResume()
        handled = false
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start()
    }

    private fun start() {
        scanner.resume()
        scanner.decodeContinuous(BarcodeCallback { result ->
            if (handled) return@BarcodeCallback
            handled = true
            onCode(result.text.trim())
        })
    }

    override fun onPause() {
        super.onPause()
        scanner.pause()
    }

    private fun onCode(code: String) {
        val url = when {
            code.startsWith("http://", true) || code.startsWith("https://", true) -> code
            code.startsWith("www.", true) -> "https://$code"
            else -> null
        }
        if (url != null) {
            startActivity(Intent(this, BrowserActivity::class.java).setData(Uri.parse(url)))
            finish()
            return
        }
        // Not a web link (e.g. WLAN password or plain text): just show it.
        val shown = if (code.startsWith("WIFI:", true)) wifiText(code) else code
        AlertDialog.Builder(this)
            .setTitle("QR-Code")
            .setMessage(shown)
            .setPositiveButton("Kopieren") { _, _ ->
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("QR", wifiPassword(code) ?: code))
                Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show()
                handled = false
            }
            .setNegativeButton("Weiter scannen") { _, _ -> handled = false }
            .setOnCancelListener { handled = false }
            .show()
    }

    private fun field(code: String, key: String) =
        Regex("(?<![\\\\])$key:((?:\\\\.|[^;])*)").find(code)?.groupValues?.get(1)?.replace("\\", "")

    private fun wifiPassword(code: String) = if (code.startsWith("WIFI:", true)) field(code, "P") else null

    private fun wifiText(code: String) =
        "WLAN: ${field(code, "S") ?: "?"}\nPasswort: ${field(code, "P") ?: "(keins)"}\n\nTippe auf „Kopieren“ und füge das Passwort in den WLAN-Einstellungen ein."
}
