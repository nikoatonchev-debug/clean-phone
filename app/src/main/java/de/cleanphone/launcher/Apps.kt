package de.cleanphone.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Telephony
import android.widget.Toast

/** One app in the curated App Store. [search] is used when the package id may differ per bank/region. */
data class StoreApp(
    val pkg: String,
    val name: String,
    val info: String,
    val category: String,
    val search: String? = null
)

object Catalog {
    val apps = listOf(
        StoreApp("com.whatsapp", "WhatsApp", "Nachrichten und Anrufe", "Empfohlen"),
        StoreApp("com.spotify.music", "Spotify", "Musik und Podcasts", "Empfohlen"),
        StoreApp("com.google.android.apps.maps", "Google Maps", "Karten und Navigation", "Empfohlen"),

        StoreApp("com.google.android.gm", "Gmail", "E-Mails von Google", "Kommunikation"),
        StoreApp("com.microsoft.office.outlook", "Outlook", "E-Mails und Kalender", "Kommunikation"),
        StoreApp("org.thoughtcrime.securesms", "Signal", "Sicherer Messenger", "Kommunikation"),
        StoreApp("us.zoom.videomeetings", "Zoom", "Videoanrufe", "Kommunikation"),
        StoreApp("com.microsoft.teams", "Microsoft Teams", "Arbeit und Schule", "Kommunikation"),

        StoreApp("de.hafas.android.db", "DB Navigator", "Bahn-Tickets und Verbindungen", "Unterwegs"),
        StoreApp("de.flixbus.app", "FlixBus", "Fernbus-Tickets", "Unterwegs"),
        StoreApp("taxi.android.client", "FREENOW", "Taxi bestellen", "Unterwegs"),
        StoreApp("com.ubercab", "Uber", "Fahrt bestellen", "Unterwegs"),

        StoreApp("com.paypal.android.p2pmobile", "PayPal", "Bezahlen und Geld senden", "Geld"),
        StoreApp("com.starfinanz.smob.android.sfinanzstatus", "Sparkasse", "Online-Banking", "Geld"),
        StoreApp("de.fiduciagad.banking.vr", "VR Banking", "Volksbanken Raiffeisenbanken", "Geld"),
        StoreApp("de.number26.android", "N26", "Online-Banking", "Geld"),
        StoreApp("com.google.android.apps.walletnfcrel", "Google Wallet", "Mit dem Handy bezahlen", "Geld"),

        StoreApp("com.google.android.calendar", "Google Kalender", "Termine im Blick", "Organisation"),
        StoreApp("com.google.android.apps.docs", "Google Drive", "Dateien und Dokumente", "Organisation"),
        StoreApp("com.google.android.keep", "Google Notizen", "Notizen und Listen", "Organisation"),
        StoreApp("com.google.android.apps.authenticator2", "Google Authenticator", "Sichere Anmelde-Codes", "Organisation"),

        StoreApp("de.dhl.paket", "Post & DHL", "Pakete verfolgen", "Alltag"),
        StoreApp("com.google.android.apps.translate", "Google Übersetzer", "Texte und Gespräche übersetzen", "Alltag"),
        StoreApp("com.lidl.eci.lidlplus", "Lidl Plus", "Coupons und Kassenbons", "Alltag"),

        StoreApp("fr.doctolib.www", "Doctolib", "Arzttermine buchen", "Gesundheit und Behörden"),
        StoreApp("com.governikus.ausweisapp2", "AusweisApp", "Online-Ausweis", "Gesundheit und Behörden"),
        StoreApp("de.materna.bbk.mobile.app", "NINA", "Warnungen bei Unwetter und Gefahr", "Gesundheit und Behörden"),

        StoreApp("com.duolingo", "Duolingo", "Sprachen lernen", "Lernen"),
    )

    val packages: Set<String> get() = apps.map { it.pkg }.toSet()
}

/** Finds Samsung's (or any phone's) own system apps and launches things. */
object Apps {

    fun installed(c: Context, pkg: String?): Boolean {
        if (pkg == null) return false
        return try {
            c.packageManager.getApplicationInfo(pkg, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun resolve(c: Context, intent: Intent): String? =
        c.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }

    private fun firstInstalled(c: Context, vararg pkgs: String) = pkgs.firstOrNull { launchable(c, it) }

    fun launchable(c: Context, pkg: String) = c.packageManager.getLaunchIntentForPackage(pkg) != null

    fun dialer(c: Context) = resolve(c, Intent(Intent.ACTION_DIAL))
    fun sms(c: Context) = Telephony.Sms.getDefaultSmsPackage(c)
    fun camera(c: Context) = firstInstalled(c, "com.sec.android.app.camera")
        ?: resolve(c, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    fun gallery(c: Context) = firstInstalled(c, "com.sec.android.gallery3d", "com.google.android.apps.photos")
        ?: resolve(c, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
    fun clock(c: Context) = firstInstalled(c, "com.sec.android.app.clockpackage", "com.google.android.deskclock")
        ?: resolve(c, Intent(AlarmClock.ACTION_SHOW_ALARMS))
    fun calculator(c: Context) = firstInstalled(c, "com.sec.android.app.popupcalculator", "com.google.android.calculator")
        ?: resolve(c, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
    fun notes(c: Context) = firstInstalled(c, "com.samsung.android.app.notes", "com.google.android.keep")
    fun contacts(c: Context) = firstInstalled(c, "com.samsung.android.app.contacts", "com.google.android.contacts")

    /** System apps that the home screen needs; never hidden by the lockdown. */
    fun systemEssentials(c: Context): Set<String> = setOfNotNull(
        dialer(c), sms(c), camera(c), gallery(c), clock(c), calculator(c), notes(c), contacts(c),
        c.packageName,
        "com.android.settings",
        "com.sec.android.app.launcher",
        "com.android.vending",
        "com.google.android.gms",
        "com.android.systemui",
        "com.samsung.android.app.watchmanager",
        "com.samsung.android.dialer",
        "com.samsung.android.messaging",
        "com.google.android.dialer",
        "com.google.android.apps.messaging",
        "com.android.phone",
        "com.samsung.android.incallui",
        "com.android.emergency",
        "com.samsung.android.emergency",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.android.providers.media.module",
        "com.google.android.providers.media.module",
    )

    fun icon(c: Context, pkg: String, mono: Boolean): Drawable? = try {
        c.packageManager.getApplicationIcon(pkg).mutate().also {
            if (mono) it.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun label(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    fun launch(c: Context, pkg: String?) {
        val i = pkg?.let { c.packageManager.getLaunchIntentForPackage(it) }
        if (i == null) {
            Toast.makeText(c, "Diese App ist nicht installiert", Toast.LENGTH_SHORT).show()
            return
        }
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openPlayStore(c: Context, app: StoreApp) {
        val target = if (app.search != null) "market://search?q=" + Uri.encode(app.search)
        else "market://details?id=" + app.pkg
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(target))
            .setPackage("com.android.vending")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            c.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(c, "Google Play ist auf diesem Handy nicht verfügbar", Toast.LENGTH_LONG).show()
        }
    }

    fun openSystem(c: Context, vararg intents: Intent) {
        for (i in intents) {
            try {
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (e: Exception) {
                // try the next one
            }
        }
        Toast.makeText(c, "Diese Einstellung gibt es auf diesem Handy nicht", Toast.LENGTH_SHORT).show()
    }

    fun component(c: Context, pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
}
