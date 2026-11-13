package de.sportgermany.handybremse

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Erkennt Reels (Instagram) und Shorts (YouTube) am Bildschirmaufbau der App.
 * Die Kennungen können sich mit App-Updates ändern, dann muss die Liste angepasst werden.
 */
object Shorts {
    val SUPPORTED: Map<String, List<String>> = mapOf(
        "com.instagram.android" to listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_media_component",
        ),
        "com.google.android.youtube" to listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_player",
            "com.google.android.youtube:id/reel_progress_bar",
            "com.google.android.youtube:id/shorts_container",
        ),
    )

    fun name(pkg: String) = if (pkg == "com.google.android.youtube") "Shorts" else "Reels"

    fun visible(root: AccessibilityNodeInfo?, pkg: String): Boolean {
        root ?: return false
        val ids = SUPPORTED[pkg] ?: return false
        return ids.any { id ->
            try {
                root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
            } catch (e: Exception) { false }
        }
    }
}
