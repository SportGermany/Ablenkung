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

        if (pkg in store.blocked) {
            if (wholeAppBlocked(pkg)) {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) handleBlocked(pkg, shorts = false)
            } else {
                checkShorts(pkg)
            }
        }
        if (pkg in Web.BROWSERS) checkBrowser(pkg)
        if (pkg in SETTINGS_PKGS) checkProtectedPage()
    }

    /** Ganze App bremsen? Bei „Nur Reels/Shorts“ nein – außer in Nachtruhe, Fokus und Sperrzeiten, da ist alles zu. */
    private fun wholeAppBlocked(pkg: String) = !store.isShortsOnly(pkg) || store.hardLocked()

    // ---------- Zeit pro App ----------

    private val usedCache = HashMap<String, Pair<Long, Int>>()

    /** Ist das eigene Tageslimit (Minuten) dieser App aufgebraucht? Wird höchstens alle 30 s neu gemessen. */
    private fun appTimeUp(pkg: String): Boolean {
        val limit = store.appLimit(pkg)
        if (limit <= 0) return false
        val now = now()
        val cached = usedCache[pkg]
        val used = if (cached != null && now - cached.first < 30_000) cached.second
            else (Apps.appMinutesToday(this, pkg) ?: return false).also { usedCache[pkg] = now to it }
        return used >= limit
    }

    /** Darf die App gerade offen sein? */
    private fun allowed(pkg: String) = store.sessionActive(pkg) && !appTimeUp(pkg)

    private var lastShortsCheck = 0L

    private fun checkShorts(pkg: String) {
        if (allowed(pkg)) return
        val now = now()
        if (now - lastShortsCheck < 400) return
        lastShortsCheck = now
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        if (root.packageName?.toString() != pkg) return
        if (Shorts.visible(root, pkg)) handleBlocked(pkg, shorts = true)
    }

    // ---------- Websites im Browser ----------

    private var lastBrowserCheck = 0L

    private fun checkBrowser(browser: String) {
        val now = now()
        if (now - lastBrowserCheck < 700) return
        lastBrowserCheck = now
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        if (root.packageName?.toString() != browser) return
        checkBrowserRoot(root, browser)
    }

    private fun checkBrowserRoot(root: AccessibilityNodeInfo, browser: String) {
        val url = Web.url(root, browser) ?: return
        val hit = Web.match(url, store.blocked) ?: return
        if (allowed(hit.pkg)) return
        val whole = wholeAppBlocked(hit.pkg)
        if (whole || hit.shorts) handleBlocked(hit.pkg, shorts = !whole && hit.shorts, returnPkg = browser)
    }

    // ---------- Pause zeigen ----------

    private fun handleBlocked(pkg: String, shorts: Boolean, returnPkg: String? = null) {
        if (allowed(pkg)) return
        val now = System.currentTimeMillis()
        if (pkg == lastPausePkg && now - lastPauseLaunch < 1_500) return
        lastPausePkg = pkg
        lastPauseLaunch = now
        val expired = store.sessionEnd(pkg) > 0
        if (expired) store.endSession(pkg)
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({ PauseActivity.start(this, pkg, expired, shorts, returnPkg) }, 250)
    }

    /** Läuft alle 5 Sekunden: Ist die Zeit abgelaufen, Nachtruhe, Fokus oder Sperrzeit begonnen, während die App offen ist? */
    private fun checkForeground() {
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg in Web.BROWSERS) { checkBrowserRoot(root, pkg); return }
        if (pkg !in store.blocked || allowed(pkg)) return
        if (wholeAppBlocked(pkg)) handleBlocked(pkg, shorts = false)
        else if (Shorts.visible(root, pkg)) handleBlocked(pkg, shorts = true)
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
