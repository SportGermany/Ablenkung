package de.sportgermany.handybremse

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.text.TextUtils

data class AppInfo(val pkg: String, val label: String)

object Apps {
    @Volatile private var cache: List<AppInfo>? = null

    fun all(context: Context, refresh: Boolean = false): List<AppInfo> {
        if (!refresh) cache?.let { return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        cache = list
        return list
    }

    fun label(context: Context, pkg: String): String =
        all(context).firstOrNull { it.pkg == pkg }?.label ?: try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) { pkg }

    /** Startet eine App direkt (ohne Bremse). */
    fun launchDirect(context: Context, pkg: String): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(i)
        return true
    }

    /** Öffnet eine App: gebremste Apps zuerst über den Pause-Bildschirm. */
    fun open(context: Context, pkg: String) {
        val store = Store(context)
        if (pkg in store.blocked && !store.sessionActive(pkg)) {
            PauseActivity.start(context, pkg, expired = false)
        } else launchDirect(context, pkg)
    }

    fun goHome(context: Context) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    /** Sinnvolle Start-Apps beim ersten Start: Telefon, Nachrichten, Kamera, Karten, WhatsApp. */
    fun defaultFavorites(context: Context): List<String> {
        val pm = context.packageManager
        val installed = all(context).map { it.pkg }.toSet()
        val result = mutableListOf<String>()
        fun add(pkg: String?) { if (pkg != null && pkg in installed && pkg !in result) result += pkg }
        add(pm.resolveActivity(Intent(Intent.ACTION_DIAL), 0)?.activityInfo?.packageName)
        add(Telephony.Sms.getDefaultSmsPackage(context))
        add("com.whatsapp")
        add(pm.resolveActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 0)?.activityInfo?.packageName)
        add("com.sec.android.app.camera")
        add("com.google.android.apps.maps")
        add("com.sec.android.app.clockpackage")
        add("com.google.android.deskclock")
        return result.take(Store.MAX_FAVORITES)
    }

    // ---------- Einrichtungs-Status ----------

    fun isDefaultLauncher(context: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }

    fun isGuardEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, GuardService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        return splitter.any { ComponentName.unflattenFromString(it) == me }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun ignoresBattery(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Minuten heute in den gebremsten Apps (nur mit Nutzungszugriff). */
    fun minutesToday(context: Context, pkgs: Set<String>): Int? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val start = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
        val ms = pkgs.sumOf { stats[it]?.totalTimeInForeground ?: 0L }
        return (ms / 60_000L).toInt()
    }
}
