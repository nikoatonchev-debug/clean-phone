package de.cleanphone.launcher

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class SettingsActivity : ScreenActivity() {
    override val screenTitle = "Einstellungen"

    override fun onResume() {
        super.onResume()
        if (prefs().getBoolean("reset_done", false)) showFinalStep() else render()
    }

    private fun render() {
        content.removeAllViews()
        val p = prefs()

        section("Aussehen")
        row("Hintergrundbild", Glyph.PHOTO, p.getString("wallpaper", "Nacht")) {
            startActivity(Intent(this, WallpaperActivity::class.java))
        }
        row("Design", Glyph.MOON, if (pal.dark) "Dunkel" else "Hell") {
            p.edit().putBoolean("dark", !pal.dark).apply()
            recreate()
        }
        row("Logos", Glyph.GRID, if (p.getBoolean("mono", false)) "Schwarz-Weiß" else "Original") {
            p.edit().putBoolean("mono", !p.getBoolean("mono", false)).apply()
            render()
        }
        row("Ganzer Bildschirm schwarz-weiß", Glyph.CONTRAST, if (Lockdown.isGrayscale(this)) "An" else "Aus") {
            if (Lockdown.canGrayscale(this)) {
                Lockdown.setGrayscale(this, !Lockdown.isGrayscale(this))
                render()
            } else {
                Toast.makeText(this, "Suche dort nach „Graustufen“", Toast.LENGTH_LONG).show()
                Apps.openSystem(this, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        section("Handy")
        row("WLAN", Glyph.WIFI) { Apps.openSystem(this, Intent(Settings.ACTION_WIFI_SETTINGS)) }
        row("Bluetooth", Glyph.BLUETOOTH) { Apps.openSystem(this, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        row("Akku und Energiesparen", Glyph.BATTERY) {
            Apps.openSystem(this, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        }
        row("Töne und Nicht stören", Glyph.BELL) { Apps.openSystem(this, Intent(Settings.ACTION_SOUND_SETTINGS)) }
        row("Anzeige und Helligkeit", Glyph.DISPLAY) { Apps.openSystem(this, Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        row("PIN und Sperrbildschirm", Glyph.LOCK) { Apps.openSystem(this, Intent(Settings.ACTION_SECURITY_SETTINGS)) }
        row("Bildschirmzeit", Glyph.HOURGLASS) {
            Apps.openSystem(
                this,
                Apps.component(this, "com.samsung.android.forest", "com.samsung.android.forest.launcher.LauncherActivity"),
                Apps.component(this, "com.google.android.apps.wellbeing", "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"),
                Intent(Settings.ACTION_SETTINGS)
            )
        }

        section("Schutz")
        row("Gesperrte Internetseiten", Glyph.GLOBE, "${Blocklist.defaults.size + Blocklist.custom(this).size}") {
            startActivity(Intent(this, BlockedSitesActivity::class.java))
        }
        val owner = Lockdown.isOwner(this)
        row("Andere Apps sperren", Glyph.SHIELD, if (owner) "Aktiv" else "Nicht eingerichtet") {
            val msg = if (owner)
                "Die volle Sperre ist aktiv. ${Lockdown.hiddenCount(this)} Apps sind versteckt und gesperrt. Sie sind nicht gelöscht und kommen beim Zurücksetzen wieder."
            else
                "Die volle Sperre ist noch nicht eingerichtet. Andere Apps sind darum nur auf der Startseite ausgeblendet, aber nicht gesperrt.\n\nZum Einrichten das Handy per USB-Kabel an den PC anschließen und „Einrichten.bat“ starten."
            AlertDialog.Builder(this).setTitle("Andere Apps sperren").setMessage(msg)
                .setPositiveButton("OK", null).show()
        }

        section("Über")
        row("Clean 1.0", Glyph.INFO, "Version") {}

        section(" ")
        row("Auf Samsung zurücksetzen", Glyph.RESET, color = pal.danger) { confirmReset() }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Auf Samsung zurücksetzen?")
            .setMessage(
                "Wenn du fortfährst, wird dieses System entfernt und dein Handy wird wieder zum normalen Samsung.\n\n" +
                    "Alle gesperrten Apps kommen zurück. Deine Fotos, Kontakte und Chats bleiben erhalten."
            )
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Zurücksetzen") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Wirklich zurücksetzen?")
                    .setMessage("Das kann nicht rückgängig gemacht werden. Danach musst du Clean neu installieren, wenn du es wieder willst.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Ja, zurücksetzen") { _, _ -> reset() }
                    .show()
            }
            .show()
    }

    private fun reset() {
        content.removeAllViews()
        content.addView(text("Einen Moment …\nAlle Apps werden zurückgeholt.", 18f, pal.text).apply {
            setPadding(dp(4), dp(32), dp(4), 0)
        })
        val app = applicationContext
        Thread {
            Lockdown.release(app)
            runOnUiThread {
                showFinalStep()
                // Give the phone a moment to switch back to the Samsung wallpaper before asking.
                content.postDelayed({ uninstallSelf() }, 2000)
            }
        }.start()
    }

    /** After the reset only one thing is left: removing Clean itself. */
    private fun showFinalStep() {
        content.removeAllViews()
        content.addView(text("Fast geschafft", 24f, pal.text, bold = true).apply { setPadding(dp(4), dp(24), 0, dp(8)) })
        content.addView(text(
            "Alle Apps sind wieder da und die Sperre ist aufgehoben. Zum Schluss Clean entfernen, dann ist dein Handy wieder ein normales Samsung.",
            16f, pal.sub
        ).apply { setPadding(dp(4), 0, dp(4), dp(20)) })
        row("Clean jetzt entfernen", Glyph.RESET, color = pal.danger) { uninstallSelf() }
    }

    private fun uninstallSelf() {
        startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
