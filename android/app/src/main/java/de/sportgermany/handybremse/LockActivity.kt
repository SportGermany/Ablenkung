package de.sportgermany.handybremse

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Freischalten: Anfrage-Nummer an Claude schicken, Code eingeben – oder Notfall-Code. */
class LockActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MSG = "msg"
        fun start(context: Context, message: String? = null) {
            context.startActivity(Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_MSG, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }

        /** Schaltet den Löschschutz ab und öffnet die Android-Deinstallation. */
        fun startUninstall(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (Apps.isAdminActive(context)) dpm.removeActiveAdmin(Apps.adminComponent(context))
            Handler(Looper.getMainLooper()).postDelayed({
                context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, 1200)
        }
    }

    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HbTheme { LockScreen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refresh++
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Anfrage-Nummer", text))
        Toast.makeText(this, "Kopiert: $text", Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun LockScreen() {
        val store = remember { Store(this) }
        @Suppress("UNUSED_EXPRESSION") refresh
        val message = intent.getStringExtra(EXTRA_MSG)
        var feedback by remember { mutableStateOf<String?>(null) }
        var emergencyDone by remember { mutableStateOf(false) }
        val lockedUntil = store.lockedOutUntil()
        val locked = lockedUntil > System.currentTimeMillis()

        fun tryCode(action: Char, input: String): Boolean {
            if (store.lockedOutUntil() > System.currentTimeMillis()) return false
            val ok = Codes.check(action, store.challenge(action), input)
            if (ok) {
                store.resetFailures()
                store.renewChallenge(action)
            } else {
                store.registerFailure()
                feedback = "Der Code stimmt nicht. Noch ${store.failuresLeft()} Versuche, dann ist 15 Minuten Pause."
                refresh++
            }
            return ok
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(C.bg)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Title("Freischalten 🔒")
            message?.let { Body(it, C.warn) }
            Body("Schick die Anfrage-Nummer an Claude. Claude fragt dich, wofür und warum, und gibt dir dann einen Code.")
            feedback?.let { Body(it, C.bad) }
            if (locked) {
                Body("Zu viele falsche Versuche. Warte bis ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(java.util.Date(lockedUntil))} Uhr.", C.bad)
            }

            if (emergencyDone) {
                Card {
                    Body("Notfall-Code richtig ✅", C.accent)
                    Body("Alles ist für 30 Minuten freigeschaltet.")
                    BigButton("Einstellungen öffnen") {
                        startActivity(Intent(this@LockActivity, SettingsActivity::class.java))
                    }
                    BigButton("Handybremse jetzt entfernen", primary = false) { startUninstall(this@LockActivity) }
                }
            }

            ActionCard(
                title = "Einstellungen ändern",
                hint = "Öffnet die Einstellungen für ${Store.SETTINGS_MINUTES} Minuten.",
                action = Codes.SETTINGS,
                store = store,
                enabled = !locked,
            ) { input ->
                if (tryCode(Codes.SETTINGS, input)) {
                    store.unlockSettings()
                    startActivity(Intent(this@LockActivity, SettingsActivity::class.java))
                    finish()
                }
            }

            ActionCard(
                title = "Handybremse entfernen",
                hint = "Schaltet den Löschschutz ab und öffnet die Deinstallation.",
                action = Codes.UNINSTALL,
                store = store,
                enabled = !locked,
            ) { input ->
                if (tryCode(Codes.UNINSTALL, input)) {
                    store.unlockUninstall()
                    startUninstall(this@LockActivity)
                }
            }

            Card {
                Text("Notfall-Code", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Body("Nur für den Notfall, wenn du Claude nicht erreichen kannst. Der Code steht in deinen Notizen.")
                var input by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(24) },
                    singleLine = true,
                    placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                BigButton("Notfall-Code prüfen", primary = false, enabled = !locked && input.isNotBlank()) {
                    if (Codes.isEmergency(input)) {
                        store.resetFailures()
                        store.unlockSettings(30)
                        store.unlockUninstall(30)
                        emergencyDone = true
                        feedback = null
                        input = ""
                    } else {
                        store.registerFailure()
                        feedback = "Der Notfall-Code stimmt nicht. Noch ${store.failuresLeft()} Versuche."
                        refresh++
                    }
                }
            }

            BigButton("Zurück zum Startbildschirm", primary = false) { Apps.goHome(this@LockActivity); finish() }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun ActionCard(
        title: String,
        hint: String,
        action: Char,
        store: Store,
        enabled: Boolean,
        onSubmit: (String) -> Unit,
    ) {
        val request = "$action-${store.challenge(action)}"
        var input by remember(request) { mutableStateOf("") }
        Card {
            Text(title, color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Body(hint)
            Body("Anfrage-Nummer:")
            Text(request, color = C.accent, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            BigButton("Nummer kopieren", primary = false) { copy(request) }
            OutlinedTextField(
                value = input,
                onValueChange = { v -> input = v.filter { it.isDigit() }.take(6) },
                singleLine = true,
                label = { Text("Code von Claude (6 Ziffern)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            BigButton("Freischalten", enabled = enabled && input.length == 6) { onSubmit(input) }
        }
    }
}
