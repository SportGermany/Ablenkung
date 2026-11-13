package de.sportgermany.handybremse

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Löschschutz: Solange dieser Geräteadministrator aktiv ist, lässt Android die App nicht deinstallieren. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Die Handybremse wird nur mit Freischalt-Code oder Notfall-Code entfernt."
}
