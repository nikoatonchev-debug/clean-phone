package de.cleanphone.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

enum class Glyph(val tileColor: Int) {
    WEATHER(Color.parseColor("#3A7BD5")),
    STEPS(Color.parseColor("#1D9E75")),
    QR(Color.parseColor("#2C2C2A")),
    STORE(Color.parseColor("#534AB7")),
    SETTINGS(Color.parseColor("#5F5E5A")),
    TORCH(Color.parseColor("#444441")),
    SUN(0), CLOUD(0), RAIN(0), SNOW(0), STORM(0), FOG(0),
    BACK(0), CLOSE(0), CHEVRON(0), LOCK(0), BLOCK(0), PHOTO(0), MOON(0), GRID(0),
    WIFI(0), BLUETOOTH(0), BATTERY(0), BELL(0), DISPLAY(0), HOURGLASS(0),
    CONTRAST(0), GLOBE(0), SHIELD(0), RESET(0), INFO(0), PLUS(0)
}

/**
 * Small vector icons drawn in code (24-unit grid). With [tile] = true the glyph is
 * drawn white on a rounded colored square so it sits next to real app logos.
 */
class GlyphIcon(
    private val glyph: Glyph,
    private val color: Int = Color.WHITE,
    private val tile: Boolean = true,
    private val tileColor: Int = glyph.tileColor,
    private val systemShape: Boolean = false
) : Drawable() {

    companion object {
        /** The phone's own icon outline (squircle on Samsung, circle on Pixel), 100x100 units. */
        val systemMask: Path? by lazy {
            runCatching {
                android.graphics.drawable.AdaptiveIconDrawable(
                    android.graphics.drawable.ColorDrawable(0), android.graphics.drawable.ColorDrawable(0)
                ).iconMask
            }.getOrNull()
        }
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var alphaValue = 255

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = minOf(b.width(), b.height()).toFloat()
        canvas.save()
        canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f)
        if (tile) {
            fill.color = tileColor
            fill.alpha = alphaValue
            val mask = if (systemShape) systemMask else null
            if (mask != null) {
                val p = Path(mask)
                p.transform(android.graphics.Matrix().apply { setScale(size / 100f, size / 100f) })
                canvas.drawPath(p, fill)
            } else {
                canvas.drawRoundRect(RectF(0f, 0f, size, size), size * 0.3f, size * 0.3f, fill)
            }
            // Glyph occupies the middle of the tile.
            val inner = size * (if (systemShape) 0.5f else 0.58f)
            canvas.translate((size - inner) / 2f, (size - inner) / 2f)
            canvas.scale(inner / 24f, inner / 24f)
        } else {
            canvas.scale(size / 24f, size / 24f)
        }
        stroke.color = color; stroke.alpha = alphaValue; stroke.strokeWidth = 2f
        fill.color = color; fill.alpha = alphaValue
        drawGlyph(canvas)
        canvas.restore()
    }

    private fun cloud(c: Canvas, dy: Float = 0f) {
        c.drawCircle(8f, 14f + dy, 4f, fill)
        c.drawCircle(13f, 11f + dy, 5f, fill)
        c.drawCircle(17.5f, 14.5f + dy, 3.5f, fill)
        c.drawRect(8f, 14f + dy, 17.5f, 18f + dy, fill)
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
        c.drawCircle(cx, cy, r, fill)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val x1 = cx + Math.cos(a).toFloat() * (r + 2f)
            val y1 = cy + Math.sin(a).toFloat() * (r + 2f)
            val x2 = cx + Math.cos(a).toFloat() * (r + 4f)
            val y2 = cy + Math.sin(a).toFloat() * (r + 4f)
            c.drawLine(x1, y1, x2, y2, stroke)
        }
    }

    private fun poly(c: Canvas, vararg pts: Float, close: Boolean = false) {
        val p = Path()
        p.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }
        if (close) p.close()
        c.drawPath(p, stroke)
    }

    private fun drawGlyph(c: Canvas) {
        when (glyph) {
            Glyph.WEATHER -> {
                val old = fill.color
                fill.color = Color.parseColor("#FFD66B"); stroke.color = fill.color
                sun(c, 8.5f, 8f, 3.2f)
                fill.color = old; stroke.color = old
                cloud(c, 2f)
            }
            Glyph.SUN -> sun(c, 12f, 12f, 4f)
            Glyph.CLOUD -> cloud(c)
            Glyph.RAIN -> {
                cloud(c, -3f)
                c.drawLine(9f, 18f, 8f, 21f, stroke); c.drawLine(13f, 18f, 12f, 21f, stroke)
                c.drawLine(17f, 18f, 16f, 21f, stroke)
            }
            Glyph.SNOW -> {
                cloud(c, -3f)
                c.drawCircle(9f, 19.5f, 1.2f, fill); c.drawCircle(13f, 21f, 1.2f, fill)
                c.drawCircle(17f, 19.5f, 1.2f, fill)
            }
            Glyph.STORM -> { cloud(c, -3f); poly(c, 13f, 16f, 10.5f, 19.5f, 13.5f, 19.5f, 11f, 23f) }
            Glyph.FOG -> {
                c.drawLine(4f, 8f, 20f, 8f, stroke); c.drawLine(6f, 12f, 18f, 12f, stroke)
                c.drawLine(4f, 16f, 20f, 16f, stroke)
            }
            Glyph.STEPS -> {
                c.save(); c.rotate(-12f, 8f, 11f)
                c.drawOval(RectF(5f, 4f, 11f, 14f), fill); c.drawCircle(8f, 17f, 2.4f, fill)
                c.restore()
                c.save(); c.rotate(12f, 16f, 9f)
                c.drawOval(RectF(13f, 2f, 19f, 12f), fill); c.drawCircle(16f, 15f, 2.4f, fill)
                c.restore()
            }
            Glyph.QR -> {
                c.drawRoundRect(RectF(3f, 3f, 10f, 10f), 1.5f, 1.5f, stroke)
                c.drawRoundRect(RectF(14f, 3f, 21f, 10f), 1.5f, 1.5f, stroke)
                c.drawRoundRect(RectF(3f, 14f, 10f, 21f), 1.5f, 1.5f, stroke)
                c.drawRect(5.5f, 5.5f, 7.5f, 7.5f, fill); c.drawRect(16.5f, 5.5f, 18.5f, 7.5f, fill)
                c.drawRect(5.5f, 16.5f, 7.5f, 18.5f, fill)
                c.drawRect(14f, 14f, 16.5f, 16.5f, fill); c.drawRect(18.5f, 14f, 21f, 16.5f, fill)
                c.drawRect(14f, 18.5f, 16.5f, 21f, fill); c.drawRect(18.5f, 18.5f, 21f, 21f, fill)
            }
            Glyph.STORE -> {
                c.drawRoundRect(RectF(4.5f, 8.5f, 19.5f, 20.5f), 2.5f, 2.5f, stroke)
                c.drawArc(RectF(8.5f, 3.5f, 15.5f, 12f), 180f, 180f, false, stroke)
            }
            Glyph.SETTINGS -> {
                for (i in 0 until 8) {
                    c.save(); c.rotate(i * 45f, 12f, 12f)
                    c.drawRoundRect(RectF(10.5f, 2f, 13.5f, 6f), 0.8f, 0.8f, fill)
                    c.restore()
                }
                c.drawCircle(12f, 12f, 6.5f, stroke)
                c.drawCircle(12f, 12f, 2.5f, stroke)
            }
            Glyph.TORCH -> {
                poly(c, 7f, 3f, 17f, 3f, 15f, 9f, 9f, 9f, close = true)
                c.drawRoundRect(RectF(9f, 9f, 15f, 21f), 1.5f, 1.5f, stroke)
                c.drawLine(12f, 13f, 12f, 15f, stroke)
            }
            Glyph.BACK -> poly(c, 15f, 5f, 8f, 12f, 15f, 19f)
            Glyph.CHEVRON -> poly(c, 9f, 5f, 16f, 12f, 9f, 19f)
            Glyph.CLOSE -> { c.drawLine(6f, 6f, 18f, 18f, stroke); c.drawLine(18f, 6f, 6f, 18f, stroke) }
            Glyph.PLUS -> { c.drawLine(12f, 5f, 12f, 19f, stroke); c.drawLine(5f, 12f, 19f, 12f, stroke) }
            Glyph.LOCK -> {
                c.drawRoundRect(RectF(5.5f, 11f, 18.5f, 20.5f), 2f, 2f, stroke)
                val p = Path(); p.moveTo(8.5f, 11f); p.lineTo(8.5f, 8f)
                p.arcTo(RectF(8.5f, 4f, 15.5f, 11f), 180f, 180f); p.lineTo(15.5f, 11f)
                c.drawPath(p, stroke)
            }
            Glyph.BLOCK -> { c.drawCircle(12f, 12f, 8.5f, stroke); c.drawLine(6f, 6f, 18f, 18f, stroke) }
            Glyph.PHOTO -> {
                c.drawRoundRect(RectF(3.5f, 5f, 20.5f, 19f), 2f, 2f, stroke)
                poly(c, 4f, 17f, 9.5f, 11.5f, 13.5f, 15.5f, 16f, 13f, 20f, 17f)
                c.drawCircle(15.5f, 9f, 1.5f, fill)
            }
            Glyph.MOON -> {
                val p = Path(); p.addCircle(12f, 12f, 8f, Path.Direction.CW)
                val cut = Path(); cut.addCircle(16.5f, 8.5f, 7f, Path.Direction.CW)
                p.op(cut, Path.Op.DIFFERENCE)
                c.drawPath(p, fill)
            }
            Glyph.GRID -> {
                c.drawRoundRect(RectF(4f, 4f, 10.5f, 10.5f), 2f, 2f, stroke)
                c.drawRoundRect(RectF(13.5f, 4f, 20f, 10.5f), 2f, 2f, stroke)
                c.drawRoundRect(RectF(4f, 13.5f, 10.5f, 20f), 2f, 2f, stroke)
                c.drawRoundRect(RectF(13.5f, 13.5f, 20f, 20f), 2f, 2f, stroke)
            }
            Glyph.WIFI -> {
                c.drawArc(RectF(2f, 5f, 22f, 25f), 225f, 90f, false, stroke)
                c.drawArc(RectF(6f, 9f, 18f, 21f), 225f, 90f, false, stroke)
                c.drawCircle(12f, 18.5f, 1.6f, fill)
            }
            Glyph.BLUETOOTH -> poly(c, 7f, 7.5f, 17f, 16.5f, 12f, 21f, 12f, 3f, 17f, 7.5f, 7f, 16.5f)
            Glyph.BATTERY -> {
                c.drawRoundRect(RectF(2.5f, 7.5f, 19f, 16.5f), 2f, 2f, stroke)
                c.drawRoundRect(RectF(20f, 10f, 22f, 14f), 0.8f, 0.8f, fill)
                c.drawRect(5f, 10f, 11f, 14f, fill)
            }
            Glyph.BELL -> {
                val p = Path(); p.moveTo(5f, 17f); p.lineTo(19f, 17f)
                p.moveTo(7f, 17f); p.lineTo(7f, 11f)
                p.arcTo(RectF(7f, 4f, 17f, 14f), 180f, 180f); p.lineTo(17f, 17f)
                c.drawPath(p, stroke)
                c.drawCircle(12f, 20f, 1.6f, fill)
            }
            Glyph.DISPLAY -> {
                c.drawRoundRect(RectF(7f, 2.5f, 17f, 21.5f), 2.5f, 2.5f, stroke)
                c.drawLine(10.5f, 18.5f, 13.5f, 18.5f, stroke)
            }
            Glyph.HOURGLASS -> {
                c.drawLine(6f, 3f, 18f, 3f, stroke); c.drawLine(6f, 21f, 18f, 21f, stroke)
                poly(c, 8f, 3f, 16f, 3f, 12f, 12f, close = true)
                poly(c, 12f, 12f, 16f, 21f, 8f, 21f, close = true)
            }
            Glyph.CONTRAST -> {
                c.drawCircle(12f, 12f, 8.5f, stroke)
                c.drawArc(RectF(3.5f, 3.5f, 20.5f, 20.5f), -90f, 180f, true, fill)
            }
            Glyph.GLOBE -> {
                c.drawCircle(12f, 12f, 8.5f, stroke)
                c.drawOval(RectF(8f, 3.5f, 16f, 20.5f), stroke)
                c.drawLine(3.5f, 12f, 20.5f, 12f, stroke)
            }
            Glyph.SHIELD -> {
                val p = Path(); p.moveTo(12f, 3f); p.lineTo(19f, 6f); p.lineTo(19f, 11f)
                p.quadTo(19f, 18f, 12f, 21f); p.quadTo(5f, 18f, 5f, 11f); p.lineTo(5f, 6f); p.close()
                c.drawPath(p, stroke)
            }
            Glyph.RESET -> {
                c.drawArc(RectF(4f, 4f, 20f, 20f), -60f, 300f, false, stroke)
                poly(c, 16f, 2.5f, 16f, 6.5f, 20f, 6.5f)
            }
            Glyph.INFO -> {
                c.drawCircle(12f, 12f, 8.5f, stroke)
                c.drawLine(12f, 11f, 12f, 16.5f, stroke); c.drawCircle(12f, 7.8f, 1.2f, fill)
            }
        }
    }

    override fun setAlpha(alpha: Int) { alphaValue = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter; fill.colorFilter = colorFilter; invalidateSelf()
    }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
