package de.sportgermany.handybremse

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Freischalt-Codes.
 *
 * Anfrage "E-123456" -> Code = HMAC-SHA256(Geheimnis, "E-123456"), erste 4 Bytes als Zahl, modulo 1 000 000, 6 Stellen.
 * Dasselbe rechnet ../../freigabe/code.py aus.
 */
object Codes {
    const val SETTINGS = 'E'
    const val UNINSTALL = 'D'

    private fun hexToBytes(hex: String) = ByteArray(hex.length / 2) {
        hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }

    fun expected(action: Char, challenge: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hexToBytes(BuildConfig.FREIGABE_SECRET), "HmacSHA256"))
        val h = mac.doFinal("$action-$challenge".toByteArray(Charsets.UTF_8))
        val v = ((h[0].toLong() and 0xff) shl 24) or ((h[1].toLong() and 0xff) shl 16) or
            ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)
        return "%06d".format(v % 1_000_000)
    }

    fun check(action: Char, challenge: String, input: String): Boolean =
        input.filter { it.isDigit() } == expected(action, challenge)

    fun isEmergency(input: String): Boolean {
        val normalized = input.uppercase().filter { it.isLetterOrDigit() }
        if (normalized.length != 16) return false
        val d = MessageDigest.getInstance("SHA-256")
            .digest((BuildConfig.NOTFALL_SALT + normalized).toByteArray(Charsets.UTF_8))
        val hex = d.joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(hex.toByteArray(), BuildConfig.NOTFALL_HASH.toByteArray())
    }
}
