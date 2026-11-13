package de.sportgermany.handybremse

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Merkt sich den letzten Absturz, damit man ihn in „Hilfe & Reparatur“ an Claude schicken kann. */
class HbApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
                getSharedPreferences("handybremse_crash", Context.MODE_PRIVATE).edit()
                    .putString("lastCrash", "Absturz am $time (Version ${BuildConfig.VERSION_NAME}):\n" + sw.toString().take(3000))
                    .commit()
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, e)
        }
    }

    companion object {
        fun lastCrash(context: Context): String? =
            context.getSharedPreferences("handybremse_crash", Context.MODE_PRIVATE).getString("lastCrash", null)

        fun clearCrash(context: Context) =
            context.getSharedPreferences("handybremse_crash", Context.MODE_PRIVATE).edit().remove("lastCrash").apply()
    }
}
