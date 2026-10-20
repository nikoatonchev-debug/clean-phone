package de.cleanphone.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Daily steps from the phone's built-in step counter. The sensor only counts
 * since the last restart, so we remember a baseline per day.
 */
object StepTracker {
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)

    fun dayKey(offset: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        return fmt.format(cal.time)
    }

    fun hasPermission(c: Context) = Build.VERSION.SDK_INT < 29 ||
        c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun available(c: Context) =
        c.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun today(c: Context) = c.prefs().getInt("steps_" + dayKey(), 0)
    fun onDay(c: Context, offset: Int) = c.prefs().getInt("steps_" + dayKey(offset), 0)
    fun goal(c: Context) = c.prefs().getInt("step_goal", 8000)

    fun update(c: Context, total: Float): Int {
        val p = c.prefs()
        val today = dayKey()
        val last = p.getFloat("step_last", -1f)
        var base = p.getFloat("step_base", -1f)
        val day = p.getString("step_day", null)
        when {
            last < 0f || base < 0f -> base = total
            day != today -> base = if (total >= last) last else 0f
            total < last -> base -= last // phone restarted today
        }
        val steps = (total - base).toInt().coerceAtLeast(0)
        p.edit()
            .putFloat("step_last", total)
            .putFloat("step_base", base)
            .putString("step_day", today)
            .putInt("steps_$today", steps)
            .apply()
        return steps
    }

    /** Reads the sensor once and calls [done] with today's steps. */
    fun read(c: Context, done: (Int) -> Unit) {
        if (!hasPermission(c)) return
        val sm = c.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sm.registerListener(object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                sm.unregisterListener(this)
                done(update(c, e.values[0]))
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }
}

class StepsActivity : ScreenActivity(), SensorEventListener {
    override val screenTitle = "Schritte"
    private lateinit var ring: RingView
    private lateinit var bars: BarsView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ring = RingView(this, pal)
        content.addView(ring, lp(-1, dp(260)).apply { topMargin = dp(12) })

        val goalRow = row("Tagesziel", Glyph.STEPS, "%,d".format(Locale.GERMANY, StepTracker.goal(this))) {}
        (goalRow.parent as View).setOnClickListener {
            val next = when (StepTracker.goal(this)) { 6000 -> 8000; 8000 -> 10000; 10000 -> 12000; else -> 6000 }
            prefs().edit().putInt("step_goal", next).apply()
            goalRow.text = "%,d".format(Locale.GERMANY, next)
            refresh()
        }

        section("Letzte 7 Tage")
        bars = BarsView(this, pal)
        content.addView(bars, lp(-1, dp(170)))

        if (!StepTracker.available(this)) {
            content.addView(text("Dieses Handy hat keinen Schrittzähler-Sensor.", 15f, pal.sub).apply {
                setPadding(dp(4), dp(16), dp(4), 0)
            })
        } else if (!StepTracker.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1)
        }
        refresh()
    }

    private fun refresh() {
        ring.set(StepTracker.today(this), StepTracker.goal(this))
        bars.set((6 downTo 0).map { StepTracker.onDay(this, -it) }, StepTracker.goal(this))
    }

    override fun onResume() {
        super.onResume()
        if (StepTracker.hasPermission(this)) {
            val sm = getSystemService(SensorManager::class.java)
            sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        getSystemService(SensorManager::class.java).unregisterListener(this)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        onResume()
    }

    override fun onSensorChanged(e: SensorEvent) {
        StepTracker.update(this, e.values[0])
        refresh()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

/** Big progress ring with the step count in the middle. */
class RingView(c: Context, private val pal: Palette) : View(c) {
    private var steps = 0
    private var goal = 8000
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = pal.line
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Glyph.STEPS.tileColor
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.text; textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-light", 0)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.sub; textAlign = Paint.Align.CENTER }

    fun set(s: Int, g: Int) { steps = s; goal = g; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val sw = size * 0.07f
        track.strokeWidth = sw; arc.strokeWidth = sw
        val r = RectF((width - size) / 2 + sw, sw, (width + size) / 2 - sw, size - sw)
        canvas.drawArc(r, 0f, 360f, false, track)
        val sweep = 360f * (steps.toFloat() / goal).coerceIn(0f, 1f)
        if (sweep > 0) canvas.drawArc(r, -90f, sweep, false, arc)
        big.textSize = size * 0.2f
        small.textSize = size * 0.065f
        canvas.drawText("%,d".format(Locale.GERMANY, steps), width / 2f, size / 2f + big.textSize * 0.3f, big)
        canvas.drawText("von %,d Schritten".format(Locale.GERMANY, goal), width / 2f, size / 2f + big.textSize * 0.95f, small)
    }
}

/** Seven small bars, today on the right. */
class BarsView(c: Context, private val pal: Palette) : View(c) {
    private var values = List(7) { 0 }
    private var goal = 8000
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.sub; textAlign = Paint.Align.CENTER
    }
    private val days = listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")

    fun set(v: List<Int>, g: Int) { values = v; goal = g; invalidate() }

    override fun onDraw(canvas: Canvas) {
        label.textSize = height * 0.1f
        val chartH = height * 0.78f
        val slot = width / 7f
        val max = maxOf(goal, values.maxOrNull() ?: 0).toFloat()
        val cal = Calendar.getInstance()
        for (i in 0 until 7) {
            val v = values[i]
            val h = (chartH * v / max).coerceAtLeast(height * 0.03f)
            val x = slot * i + slot * 0.25f
            bar.color = if (v >= goal) Glyph.STEPS.tileColor else pal.line
            if (i == 6 && v < goal) bar.color = pal.accent
            canvas.drawRoundRect(RectF(x, chartH - h, x + slot * 0.5f, chartH), 12f, 12f, bar)
            val c2 = cal.clone() as Calendar
            c2.add(Calendar.DAY_OF_YEAR, i - 6)
            canvas.drawText(days[c2.get(Calendar.DAY_OF_WEEK) - 1], slot * i + slot / 2, height * 0.97f, label)
        }
    }
}
