package de.sportgermany.handybremse

import android.view.accessibility.AccessibilityNodeInfo

/** Erkennt TikTok, Instagram und YouTube im Browser an der Adresszeile. */
object Web {
    /** Browser -> mögliche Kennungen der Adresszeile. */
    val BROWSERS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
        "com.sec.android.app.sbrowser" to listOf(
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.sec.android.app.sbrowser:id/custom_tab_toolbar_url_bar_text",
        ),
        "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),
        "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
        "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
        "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
    )

    private val TIKTOK = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go")

    data class Hit(val pkg: String, val shorts: Boolean)

    fun url(root: AccessibilityNodeInfo?, browser: String): String? {
        root ?: return null
        val ids = BROWSERS[browser] ?: return null
        for (id in ids) {
            val t = try { root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.text?.toString() } catch (e: Exception) { null }
            if (!t.isNullOrBlank()) return t.lowercase()
        }
        return null
    }

    /** Welche gebremste App gehört zu dieser Adresse? */
    fun match(url: String, blocked: Set<String>): Hit? {
        val u = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").removePrefix("m.")
        val host = u.substringBefore('/').substringBefore('?')
        val path = u.substringAfter('/', "")
        return when {
            host == "tiktok.com" || host.endsWith(".tiktok.com") ->
                TIKTOK.firstOrNull { it in blocked }?.let { Hit(it, false) }
            host == "instagram.com" || host.endsWith(".instagram.com") ->
                "com.instagram.android".takeIf { it in blocked }?.let { Hit(it, path.startsWith("reel")) }
            host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" ->
                "com.google.android.youtube".takeIf { it in blocked }?.let { Hit(it, path.startsWith("shorts")) }
            else -> null
        }
    }
}
