package de.sportgermany.handybremse

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online-Updates: liest android/version.json aus dem GitHub-Repo.
 * Die neue APK wird über den Browser geladen und installiert (die App selbst braucht dafür kein Installationsrecht).
 */
object Updater {
    private val VERSION_URLS = listOf(
        "https://raw.githubusercontent.com/SportGermany/Ablenkung/main/android/version.json",
        "https://raw.githubusercontent.com/SportGermany/Ablenkung/ccr-c3197237-hkf8yg/android/version.json",
    )
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    data class Update(val code: Int, val name: String, val apk: String, val notes: String)

    private fun prefs(c: Context) = c.getSharedPreferences("handybremse_update", Context.MODE_PRIVATE)

    /** Gemerktes verfügbares Update (nur wenn neuer als die installierte Version). */
    fun available(c: Context): Update? {
        val p = prefs(c)
        val code = p.getInt("code", 0)
        if (code <= BuildConfig.VERSION_CODE) return null
        return Update(code, p.getString("name", "?")!!, p.getString("apk", "")!!, p.getString("notes", "")!!)
    }

    private fun fetch(url: String): String? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 8_000; readTimeout = 8_000
            setRequestProperty("Cache-Control", "no-cache")
            try { if (responseCode == 200) inputStream.bufferedReader().readText() else null } finally { disconnect() }
        }
    } catch (e: Exception) { null }

    /**
     * Prüft online (nicht im UI-Thread aufrufen). force = auch wenn erst kürzlich geprüft.
     * Ergebnis: das Update, null = aktuell, oder wirft eine Exception, wenn keine Verbindung.
     */
    fun check(c: Context, force: Boolean = false): Update? {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong("lastCheck", 0) < CHECK_EVERY_MS) return available(c)
        var best: JSONObject? = null
        var reached = false
        for (u in VERSION_URLS) {
            val text = fetch(u) ?: continue
            reached = true
            val j = try { JSONObject(text) } catch (e: Exception) { continue }
            if (best == null || j.optInt("versionCode") > best.optInt("versionCode")) best = j
        }
        if (!reached) throw IllegalStateException("Keine Verbindung zum Update-Server.")
        p.edit().putLong("lastCheck", now).apply()
        best?.let { j ->
            p.edit()
                .putInt("code", j.optInt("versionCode"))
                .putString("name", j.optString("versionName"))
                .putString("apk", j.optString("apk"))
                .putString("notes", j.optString("notes"))
                .apply()
        }
        return available(c)
    }

    /** Öffnet den Download im Browser. Danach in der Download-Benachrichtigung auf „Öffnen“ tippen und installieren. */
    fun download(c: Context, u: Update) {
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apk)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
