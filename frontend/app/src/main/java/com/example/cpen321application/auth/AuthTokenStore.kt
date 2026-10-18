package com.example.cpen321application.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
    Keeps the server's JWT on the device between app launches.
    The token is encrypted with an AES key that lives in the Android Keystore
    (it never leaves secure hardware), and the encrypted bytes are saved in the
    app's private SharedPreferences
 */
object AuthTokenStore {
    private const val PREFS_NAME = "auth"
    private const val TOKEN_KEY = "jwt_token"
    private const val KEY_ALIAS = "sportsnow_jwt_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    fun save(context: Context, token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        // store the random iv in front of the encrypted token
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs(context).edit()
            .putString(TOKEN_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(TOKEN_KEY).apply()
    }

    /*  returns the saved token, or null if there is none. An expired token is still
        returned: the server reports it as expired and the app renews it silently
     */
    fun load(context: Context): String? {
        val stored = prefs(context).getString(TOKEN_KEY, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES)
            )
            val plain = cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES)
            String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            // key was lost (e.g. app data restored to another phone), the user signs in again
            clear(context)
            null
        }
    }

    // user_id the server put in the token, null if it can't be read
    fun userIdOf(token: String): Int? {
        val payload = decodePayload(token) ?: return null
        return if (payload.has("user_id")) payload.optInt("user_id") else null
    }

    // the JWT's middle part is base64 encoded JSON with its fields
    private fun decodePayload(token: String): JSONObject? {
        return try {
            val payload = token.split(".")[1]
            val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            JSONObject(String(Base64.decode(payload, flags)))
        } catch (e: Exception) {
            null
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
