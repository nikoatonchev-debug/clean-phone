package de.cleanphone.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.concurrent.thread

/** Built-in flat backgrounds. "Weiß" is the one every phone starts with. */
data class Preset(val name: String, val dark: Boolean, val bg: String, val a: String, val b: String, val cc: String)

object Wallpapers {
    val presets = listOf(
        Preset("Weiß", false, "#FBFBFA", "#F3F3F1", "#F6F6F4", "#EFEFEC"),
        Preset("Nebel", false, "#F2F5F9", "#E6ECF4", "#ECF0F6", "#DDE5F0"),
        Preset("Salbei", false, "#F2F5F1", "#E5ECE3", "#EBF0E9", "#DAE4D7"),
        Preset("Sand", false, "#F7F4EE", "#EEE8DD", "#F2EEE6", "#E6DECF"),
        Preset("Nacht", true, "#0B0B0C", "#161618", "#121214", "#1E1E21"),
        Preset("Tiefblau", true, "#0B1220", "#1B2A4A", "#14213D", "#25365E"),
    )

    /** Same three soft circles as the preview, scaled to any screen. */
    fun render(p: Preset, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.parseColor(p.bg))
        val sx = w / 150f
        val sy = h / 330f
        val s = minOf(sx, sy * 1.0f).coerceAtLeast(sx)
        paint.color = Color.parseColor(p.a); c.drawCircle(130 * sx, 70 * sy, 90 * s, paint)
        paint.color = Color.parseColor(p.b); c.drawCircle(10 * sx, 260 * sy, 110 * s, paint)
        paint.color = Color.parseColor(p.cc); c.drawCircle(110 * sx, 300 * sy, 60 * s, paint)
        return bmp
    }

    fun screenSize(c: Context): Pair<Int, Int> {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        c.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(dm)
        return minOf(dm.widthPixels, dm.heightPixels) to maxOf(dm.widthPixels, dm.heightPixels)
    }

    private fun set(c: Context, bmp: Bitmap) {
        WallpaperManager.getInstance(c)
            .setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
    }

    fun apply(c: Context, p: Preset, done: () -> Unit = {}) {
        val app = c.applicationContext
        app.prefs().edit().putString("wallpaper", p.name).putBoolean("dark", p.dark).apply()
        thread {
            val (w, h) = screenSize(app)
            runCatching { set(app, render(p, w, h)) }
            Handler(Looper.getMainLooper()).post(done)
        }
    }

    /** Center-crops a picked photo to the screen and uses it on home and lock screen. */
    fun applyPhoto(c: Context, uri: Uri, done: (Boolean) -> Unit) {
        val app = c.applicationContext
        thread {
            val ok = runCatching {
                val (w, h) = screenSize(app)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / (sample * 2) >= w && opts.outHeight / (sample * 2) >= h) sample *= 2
                val src = app.contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }!!
                val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
                val cw = (w / scale).toInt(); val ch = (h / scale).toInt()
                val left = (src.width - cw) / 2; val top = (src.height - ch) / 2
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Canvas(out).drawBitmap(src, Rect(left, top, left + cw, top + ch), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                set(app, out)
                app.prefs().edit().putString("wallpaper", "Foto").apply()
            }.isSuccess
            Handler(Looper.getMainLooper()).post { done(ok) }
        }
    }
}

class WallpaperActivity : ScreenActivity() {
    override val screenTitle = "Hintergrundbild"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = prefs().getString("wallpaper", "Weiß")
        val grid = GridLayout(this).apply { columnCount = 3 }
        val cellW = (resources.displayMetrics.widthPixels - dp(40) - dp(24)) / 3
        val cellH = (cellW * 1.9f).toInt()
        for (p in Wallpapers.presets) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val frame = FrameLayout(this).apply {
                background = roundRect(Color.TRANSPARENT, dp(18).toFloat(), if (p.name == current) dp(2) else dp(1), if (p.name == current) pal.text else pal.line)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            val img = ImageView(this).apply {
                setImageDrawable(BitmapDrawable(resources, Wallpapers.render(p, cellW, cellH)))
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: android.view.View, o: android.graphics.Outline) {
                        o.setRoundRect(0, 0, v.width, v.height, dp(12).toFloat())
                    }
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            frame.addView(img, FrameLayout.LayoutParams(-1, -1))
            frame.setOnClickListener {
                Wallpapers.apply(this, p) {
                    Toast.makeText(this, "Hintergrund „${p.name}“ gesetzt", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
            cell.addView(frame, LinearLayout.LayoutParams(cellW, cellH))
            cell.addView(text(if (p.name == "Weiß") "Weiß (Standard)" else p.name, 13f, pal.sub).apply {
                setPadding(0, dp(6), 0, dp(14))
            })
            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = cellW; setMargins(0, 0, if (grid.childCount % 3 == 2) 0 else dp(12), 0)
            })
        }
        content.addView(grid, lp())
        section("Eigenes Bild")
        row("Foto aus der Galerie", Glyph.PHOTO) {
            val pick = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES)
            else Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
            runCatching { startActivityForResult(pick, 7) }.onFailure {
                Toast.makeText(this, "Keine Bildauswahl gefunden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data ?: return
        if (requestCode != 7 || resultCode != RESULT_OK) return
        Toast.makeText(this, "Einen Moment …", Toast.LENGTH_SHORT).show()
        Wallpapers.applyPhoto(this, uri) { ok ->
            Toast.makeText(this, if (ok) "Foto als Hintergrund gesetzt" else "Das Foto ging leider nicht", Toast.LENGTH_SHORT).show()
            if (ok) recreate()
        }
    }
}
