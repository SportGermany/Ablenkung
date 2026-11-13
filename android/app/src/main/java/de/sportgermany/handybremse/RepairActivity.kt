package de.sportgermany.handybremse

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/** Hilfe & Reparatur: Neustart, Aufräumen, Sicherung. Neustart und Sichern gehen ohne Code. */
class RepairActivity : ComponentActivity() {

    private var message by mutableStateOf<String?>(null)

    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        message = try {
            contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(this).toByteArray()) }
            "✅ Sicherung gespeichert. Bewahre die Datei gut auf, z. B. in Google Drive."
        } catch (e: Exception) { "❌ Speichern hat nicht geklappt: ${e.message}" }
    }

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        restore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HbTheme { Screen() } }
    }

    private fun restore(uri: Uri) {
        if (!Store(this).settingsUnlocked()) {
            message = "🔒 Zum Wiederherstellen müssen die Einstellungen offen sein (Code von Claude oder Notfall-Code)."
            return
        }
        message = try {
            val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Datei leer")
            val n = Backup.import(this, json)
            "✅ Sicherung wiederhergestellt ($n Werte). Starte die App am besten einmal neu."
        } catch (e: Exception) { "❌ Wiederherstellen hat nicht geklappt: ${e.message}" }
    }

    /** Startet die Handybremse komplett neu (auch den Wächter). */
    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val options = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle() else null
        val pi = PendingIntent.getActivity(this, 7, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT, options)
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.RTC, System.currentTimeMillis() + 800, pi)
        finishAffinity()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    @Composable
    private fun Screen() {
        var confirmRestart by remember { mutableStateOf(false) }
        var crash by remember { mutableStateOf(HbApp.lastCrash(this)) }
        Column(
            Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Title("🛠 Hilfe & Reparatur")
            Body("Version ${BuildConfig.VERSION_NAME}")
            message?.let { Card { Body(it, C.text) } }

            Card {
                Text("🔄 Neustart", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Body("Wenn etwas hakt: Die Handybremse und der Wächter starten komplett neu. Deine Einstellungen bleiben, und die Bremse bleibt aktiv.")
                if (!confirmRestart) BigButton("Handybremse neu starten") { confirmRestart = true }
                else {
                    Body("Der Bildschirm ist kurz schwarz, dann kommt der Startbildschirm wieder.", C.warn)
                    BigButton("Ja, jetzt neu starten") { restartApp() }
                    BigButton("Abbrechen", primary = false) { confirmRestart = false }
                }
            }

            Card {
                Text("🧹 Reparieren", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Body("Räumt hängende Zustände und den Zwischenspeicher auf. Einstellungen, Statistik, Abzeichen und dein Wald bleiben erhalten.")
                BigButton("Jetzt reparieren", primary = false) {
                    message = "✅ Repariert:\n• " + Backup.repair(this@RepairActivity).joinToString("\n• ") +
                        "\nWenn es noch hakt: einmal neu starten."
                }
            }

            Card {
                Text("💾 Sicherung", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Body("Speichert Einstellungen, Statistik, Abzeichen und deinen Fokus-Wald als Datei, z. B. für ein neues Handy.")
                BigButton("Sicherung speichern", primary = false) {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
                    saveFile.launch("Handybremse-Sicherung-$date.json")
                }
                Body("Wiederherstellen ändert deine Einstellungen. Darum geht es nur, wenn die Einstellungen gerade mit Code offen sind.")
                BigButton("Sicherung wiederherstellen", primary = false) {
                    if (!Store(this@RepairActivity).settingsUnlocked()) {
                        message = "🔒 Zum Wiederherstellen müssen die Einstellungen offen sein. Schick Claude die Anfrage-Nummer unter „🔒 Einstellungen“."
                    } else openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
            }

            crash?.let { c ->
                Card {
                    Text("🐞 Letzter Absturz", color = C.warn, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Body("Kopier den Fehlerbericht und schick ihn Claude, dann kann der Fehler behoben werden.")
                    BigButton("Fehlerbericht kopieren", primary = false) {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Handybremse-Fehler", c))
                        Toast.makeText(this@RepairActivity, "Kopiert! Füg ihn im Chat mit Claude ein.", Toast.LENGTH_LONG).show()
                    }
                    Text("Bericht löschen", color = C.dim, fontSize = 14.sp,
                        modifier = Modifier.clickable { HbApp.clearCrash(this@RepairActivity); crash = null }
                            .padding(vertical = 4.dp))
                }
            }

            BigButton("Zurück", primary = false) { finish() }
            Spacer(Modifier.height(16.dp))
        }
    }
}
