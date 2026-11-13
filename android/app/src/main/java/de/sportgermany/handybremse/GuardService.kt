package de.sportgermany.handybremse

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Der Wächter: merkt, wenn eine gebremste App in den Vordergrund kommt, und zeigt die Pause.
 * Schützt außerdem die System-Seiten, auf denen man die Handybremse abschalten oder löschen könnte.
 */
class GuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private var lastPauseLaunch = 0L
    private var lastPausePkg: String? = null
    private var lastSettingsCheck = 0L

    private val ticker = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, 5_000)
        }
    }

    companion object {
        private val SETTINGS_PKGS = setOf(
            "com.android.settings",
            "com.samsung.accessibility",
            "com.samsung.android.accessibility",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.samsung.android.lool",
            "com.samsung.android.sm.devicesecurity",
        )

        /** Wörter, die auf einer Seite stehen, auf der man die Handybremse stoppen/löschen/abschalten könnte. */
        private val DANGER_WORDS = listOf(
            "deinstall", "uninstall", "erzwingen", "force stop", "deaktivieren", "deactivate",
            "daten löschen", "speicher löschen", "clear data", "clear storage", "verknüpfung", "shortcut",
            "geräteadministrator", "device admin", "admin-app", "verwenden", "use ",
            "deaktiviert", "ausschalten", "turn off",
        )
        private val DANGER_EXACT = setOf("ein", "aus", "on", "off")
    }

    private var graceUntil = 0L
    private fun now() = System.currentTimeMillis()

    override fun onServiceConnected() {
        store = Store(this)
        if (store.firstGuardStart()) graceUntil = now() + 30_000
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!::store.isInitialized) store = Store(this)
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg in store.blocked) {
            handleBlocked(pkg)
        }
        if (pkg in SETTINGS_PKGS) checkProtectedPage()
    }

    private fun handleBlocked(pkg: String) {
        if (store.sessionActive(pkg)) return
        val now = System.currentTimeMillis()
        if (pkg == lastPausePkg && now - lastPauseLaunch < 1_500) return
        lastPausePkg = pkg
        lastPauseLaunch = now
        val expired = store.sessionEnd(pkg) > 0
        if (expired) store.endSession(pkg)
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({ PauseActivity.start(this, pkg, expired) }, 250)
    }

    /** Läuft alle 5 Sekunden: Ist die Zeit in einer App abgelaufen, während sie offen ist? */
    private fun checkForeground() {
        val pkg = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null } ?: return
        if (pkg in store.blocked && !store.sessionActive(pkg)) handleBlocked(pkg)
    }

    private fun checkProtectedPage() {
        if (now() < graceUntil) return              // kurz nach dem allerersten Einschalten: Einrichtung abschließen lassen
        if (store.uninstallUnlocked()) return       // mit Code freigeschaltet
        val now = System.currentTimeMillis()
        if (now - lastSettingsCheck < 300) return
        lastSettingsCheck = now

        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        if (root.packageName?.toString() !in SETTINGS_PKGS) return
        val texts = ArrayList<String>()
        collectTexts(root, texts, 0)
        val appName = getString(R.string.app_name).lowercase()
        if (texts.none { it.contains(appName) }) return
        val danger = texts.any { t -> t in DANGER_EXACT || DANGER_WORDS.any { t.contains(it) } }
        if (!danger) return

        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            LockActivity.start(this, "Diese Seite ist geschützt. Zum Abschalten oder Löschen brauchst du einen Freischalt-Code.")
        }, 250)
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40 || out.size > 600) return
        node.text?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { out += it }
        node.contentDescription?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { out += it }
        for (i in 0 until node.childCount) {
            collectTexts(try { node.getChild(i) } catch (e: Exception) { null }, out, depth + 1)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
