// SPDX-License-Identifier: CC0-1.0
package org.getflourish.beeswax

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

// The lock code. The first Lock (or New code) makes a random code and shows it once, and the same
// code is used until Deactivate or New code. Only a salted hash of it is kept, in Beeswax's
// private storage, which Android won't let Settings clear for a device owner.
// 16 characters from a 32-character alphabet is 80 random bits, far too many to guess, so there's
// no limit on tries.
object Code {
    // Lowercase letters and digits, without 0, 1, l and o, which are easy to mix up.
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

    // Four groups of four, like k7mq-x3tp-9wfh-r2ne.
    fun create(): String {
        val random = SecureRandom()
        return (1..16).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("").chunked(4).joinToString("-")
    }

    fun save(context: Context, code: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit().clear()
            .putString("salt", encode(salt))
            .putString("hash", encode(hash(code, salt)))
            .commit()
    }

    fun exists(context: Context) = prefs(context).contains("hash")

    // For checking a newly made code before it's saved.
    fun same(code: String, typed: String) = normalize(code) == normalize(typed)

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    fun matches(context: Context, typed: String): Boolean {
        val salt = prefs(context).getString("salt", null) ?: return false
        val stored = prefs(context).getString("hash", null) ?: return false
        return MessageDigest.isEqual(hash(typed, decode(salt)), decode(stored))
    }

    private fun hash(code: String, salt: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run {
        update(salt)
        digest(normalize(code).toByteArray())
    }

    // Capitals, spaces and dashes don't matter when typing the code.
    private fun normalize(code: String) = code.lowercase().filter { it != '-' && !it.isWhitespace() }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String) = Base64.getDecoder().decode(text)

    private fun prefs(context: Context) = context.getSharedPreferences("code", Context.MODE_PRIVATE)
}
