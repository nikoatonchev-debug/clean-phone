package de.cleanphone.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** Weather from Open-Meteo (free, no account). Cached so the home screen can show it instantly. */
object Weather {

    fun glyph(code: Int) = when (code) {
        0, 1 -> Glyph.SUN
        2, 3 -> Glyph.CLOUD
        45, 48 -> Glyph.FOG
        in 51..67, in 80..82 -> Glyph.RAIN
        in 71..77, 85, 86 -> Glyph.SNOW
        in 95..99 -> Glyph.STORM
        else -> Glyph.CLOUD
    }

    fun describe(code: Int) = when (code) {
        0 -> "Klar"
        1 -> "Überwiegend sonnig"
        2 -> "Teilweise bewölkt"
        3 -> "Bewölkt"
        45, 48 -> "Nebel"
        in 51..57 -> "Nieselregen"
        in 61..67 -> "Regen"
        in 71..77 -> "Schnee"
        in 80..82 -> "Regenschauer"
        85, 86 -> "Schneeschauer"
        in 95..99 -> "Gewitter"
        else -> "–"
    }

    fun cached(c: Context): JSONObject? =
        c.prefs().getString("weather_json", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun currentTemp(c: Context): String? = cached(c)?.optJSONObject("current")
        ?.optDouble("temperature_2m")?.takeIf { !it.isNaN() }?.let { "${it.roundToInt()}°" }

    fun currentCode(c: Context): Int = cached(c)?.optJSONObject("current")?.optInt("weather_code", 3) ?: 3

    fun place(c: Context) = c.prefs().getString("weather_place", null)

    fun stale(c: Context) = System.currentTimeMillis() - c.prefs().getLong("weather_time", 0) > 30 * 60_000

    private fun get(url: String): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 10_000; con.readTimeout = 10_000
        return con.inputStream.bufferedReader().use { it.readText() }
    }

    private fun lastLocation(c: Context): Location? {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = c.getSystemService(LocationManager::class.java)
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Refreshes in the background, then calls [done] on the main thread (true = success). */
    fun refresh(c: Context, done: (Boolean) -> Unit = {}) {
        val app = c.applicationContext
        val main = Handler(Looper.getMainLooper())
        thread {
            val ok = runCatching {
                val p = app.prefs()
                var lat = p.getFloat("weather_lat", Float.NaN).toDouble()
                var lon = p.getFloat("weather_lon", Float.NaN).toDouble()
                val manual = p.getBoolean("weather_manual", false)
                if (!manual) {
                    lastLocation(app)?.let { loc ->
                        lat = loc.latitude; lon = loc.longitude
                        val name = runCatching {
                            @Suppress("DEPRECATION")
                            Geocoder(app, Locale.GERMANY).getFromLocation(lat, lon, 1)?.firstOrNull()?.locality
                        }.getOrNull()
                        p.edit().putFloat("weather_lat", lat.toFloat()).putFloat("weather_lon", lon.toFloat())
                            .putString("weather_place", name ?: "Dein Standort").apply()
                    }
                }
                if (lat.isNaN()) error("Kein Ort")
                val json = get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                        "&timezone=auto&forecast_days=7"
                )
                JSONObject(json)
                p.edit().putString("weather_json", json).putLong("weather_time", System.currentTimeMillis()).apply()
            }.isSuccess
            main.post { done(ok) }
        }
    }

    /** Looks up a city by name and stores it as a fixed place. */
    fun setCity(c: Context, name: String, done: (Boolean) -> Unit) {
        val app = c.applicationContext
        val main = Handler(Looper.getMainLooper())
        thread {
            val ok = runCatching {
                val q = URLEncoder.encode(name, "UTF-8")
                val r = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=de"))
                val hit = r.getJSONArray("results").getJSONObject(0)
                app.prefs().edit()
                    .putFloat("weather_lat", hit.getDouble("latitude").toFloat())
                    .putFloat("weather_lon", hit.getDouble("longitude").toFloat())
                    .putString("weather_place", hit.getString("name"))
                    .putBoolean("weather_manual", true)
                    .apply()
            }.isSuccess
            main.post { if (ok) refresh(app, done) else done(false) }
        }
    }
}

class WeatherActivity : ScreenActivity() {
    override val screenTitle = "Wetter"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            !prefs().getBoolean("weather_manual", false)
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else {
            Weather.refresh(this) { render() }
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        Weather.refresh(this) { render() }
    }

    private fun render() {
        content.removeAllViews()
        val w = Weather.cached(this)
        if (w == null) {
            content.addView(text("Wetter wird geladen …\n\nFalls nichts erscheint: Internet prüfen oder unten einen Ort eingeben.", 16f, pal.sub).apply {
                setPadding(dp(4), dp(24), dp(4), dp(24))
            })
        } else {
            val cur = w.getJSONObject("current")
            val code = cur.optInt("weather_code", 3)
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(12), 0, dp(20))
            }
            top.addView(text(Weather.place(this) ?: "", 18f, pal.sub))
            top.addView(ImageView(this).apply {
                setImageDrawable(GlyphIcon(Weather.glyph(code), pal.text, tile = false))
            }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { topMargin = dp(12) })
            top.addView(text("${cur.optDouble("temperature_2m").roundToInt()}°", 72f, pal.text, light = true))
            top.addView(text(Weather.describe(code), 18f, pal.text))
            top.addView(text(
                "Gefühlt ${cur.optDouble("apparent_temperature").roundToInt()}° · Wind ${cur.optDouble("wind_speed_10m").roundToInt()} km/h · Feuchte ${cur.optInt("relative_humidity_2m")} %",
                13f, pal.sub
            ).apply { setPadding(0, dp(6), 0, 0) })
            for (i in 0 until top.childCount) (top.getChildAt(i) as? android.widget.TextView)?.gravity = Gravity.CENTER
            content.addView(top, lp())

            section("Nächste Tage")
            val d = w.getJSONObject("daily")
            val times = d.getJSONArray("time")
            val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
            val outFmt = SimpleDateFormat("EEEE", Locale.GERMANY)
            for (i in 0 until times.length()) {
                val day = if (i == 0) "Heute" else outFmt.format(inFmt.parse(times.getString(i))!!)
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    background = roundRect(pal.card, dp(14).toFloat())
                }
                r.addView(text(day, 16f, pal.text), LinearLayout.LayoutParams(0, -2, 1f))
                val rain = d.getJSONArray("precipitation_probability_max").optInt(i, 0)
                if (rain >= 20) r.addView(text("$rain %  ", 13f, pal.accent))
                r.addView(ImageView(this).apply {
                    setImageDrawable(GlyphIcon(Weather.glyph(d.getJSONArray("weather_code").getInt(i)), pal.text, tile = false))
                }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(16) })
                r.addView(text(
                    "${d.getJSONArray("temperature_2m_max").getDouble(i).roundToInt()}° / ${d.getJSONArray("temperature_2m_min").getDouble(i).roundToInt()}°",
                    16f, pal.text
                ))
                content.addView(r, lp().apply { bottomMargin = dp(8) })
            }
        }

        section("Ort")
        val manual = prefs().getBoolean("weather_manual", false)
        row(if (manual) "Wieder meinen Standort benutzen" else "Anderen Ort eingeben", Glyph.GLOBE) {
            if (manual) {
                prefs().edit().putBoolean("weather_manual", false).apply()
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            } else askCity()
        }
    }

    private fun askCity() {
        val input = EditText(this).apply { hint = "z. B. München"; setSingleLine() }
        android.app.AlertDialog.Builder(this)
            .setTitle("Ort eingeben")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input, lp()) })
            .setPositiveButton("Übernehmen") { _, _ ->
                Weather.setCity(this, input.text.toString().trim()) { ok ->
                    if (!ok) android.widget.Toast.makeText(this, "Ort nicht gefunden", android.widget.Toast.LENGTH_SHORT).show()
                    render()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
