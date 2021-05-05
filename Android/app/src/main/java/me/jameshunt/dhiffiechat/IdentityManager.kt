package me.jameshunt.dhiffiechat

import android.content.SharedPreferences
import android.util.Log
import me.jameshunt.dhiffiechat.crypto.*
import java.security.KeyPair

class IdentityManager(private val getSharedPreferences: () -> SharedPreferences) {
    private var cached: KeyPair? = null

    fun getIdentity(): KeyPair = synchronized(this) {
        get() ?: new().also { save(it) }
    }

    private fun get(): KeyPair? {
        cached?.let { return it }

        val sharedPreferences = getSharedPreferences()
        val privateBase64 = sharedPreferences.getString("private", null) ?: return null
        val publicBase64 = sharedPreferences.getString("public", null) ?: return null

        Log.d("public base64", publicBase64)
        Log.d("private base64", privateBase64)
        return KeyPair(publicBase64.toPublicKey(), privateBase64.toPrivateKey())
            .also { cached = it }
    }

    private fun new(): KeyPair = DHCrypto.genDHKeyPair()

    private fun save(keyPair: KeyPair) {
        cached = keyPair

        getSharedPreferences().edit()
            .putString("private", keyPair.private.toBase64String())
            .putString("public", keyPair.public.toBase64String())
            .apply()
    }
}

fun KeyPair.toUserId(): String = public.toUserId()