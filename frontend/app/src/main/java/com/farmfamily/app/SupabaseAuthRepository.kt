package com.farmfamily.app

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String,
    val displayName: String = ""
)

class SupabaseAuthRepository(context: Context) {
    private val sessionStore = SecureSessionStore(context.applicationContext)
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    fun signIn(email: String, password: String): AuthSession {
        require(email.isNotBlank() && password.isNotBlank()) { "Enter your email and password." }
        return authToken(
            "token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password)
        ).also(sessionStore::save)
    }

    fun signUp(email: String, password: String, fullName: String): AuthSession? {
        require(email.isNotBlank() && password.isNotBlank() && fullName.isNotBlank()) {
            "Enter your name, email, and password."
        }
        val response = request(
            "POST",
            "signup?redirect_to=farmfamily%3A%2F%2Fauth-callback",
            body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("data", JSONObject().put("full_name", fullName.trim()))
        )
        if (!response.has("access_token")) return null
        return response.toSession().also(sessionStore::save)
    }

    fun sendPasswordRecovery(email: String) {
        require(email.isNotBlank()) { "Enter your account email." }
        request(
            "POST",
            "recover?redirect_to=farmfamily%3A%2F%2Fauth-callback",
            body = JSONObject().put("email", email.trim())
        )
    }

    fun handleAuthCallback(uri: Uri): AuthCallback {
        if (uri.scheme != "farmfamily" || uri.host != "auth-callback") {
            throw IOException("This is not a FARM FAMILY sign-in link.")
        }
        val parameters = sequenceOf(uri.query, uri.fragment)
            .filterNotNull()
            .flatMap { it.split('&').asSequence() }
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null
                else Uri.decode(part.substring(0, separator)) to Uri.decode(part.substring(separator + 1))
            }
            .toMap()
        val accessToken = parameters["access_token"].orEmpty()
        val refreshToken = parameters["refresh_token"].orEmpty()
        if (accessToken.isBlank() || refreshToken.isBlank()) {
            throw IOException("The email link did not contain a session. Request a new link and try again.")
        }

        val user = request("GET", "user", accessToken = accessToken)
        val session = AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = parameters["expires_at"]?.toLongOrNull()
                ?: (System.currentTimeMillis() / 1000 + (parameters["expires_in"]?.toLongOrNull() ?: 3600)),
            userId = user.optString("id").takeIf(String::isNotBlank)
                ?: throw IOException("The sign-in link did not identify an account."),
            email = user.optString("email"),
            displayName = user.optJSONObject("user_metadata")
                ?.optString("full_name")
                .orEmpty()
                .ifBlank { user.optJSONObject("user_metadata")?.optString("name").orEmpty() }
        )
        sessionStore.save(session)
        return AuthCallback(session, parameters["type"] == "recovery")
    }

    fun updatePassword(accessToken: String, password: String) {
        require(password.isNotBlank()) { "Enter a new password." }
        request(
            "PUT",
            "user",
            body = JSONObject().put("password", password),
            accessToken = accessToken
        )
    }

    fun restoreSession(): AuthSession? {
        val saved = sessionStore.load() ?: return null
        if (saved.expiresAt <= System.currentTimeMillis() / 1000 + 60) {
            return refresh(saved.refreshToken)
        }

        try {
            val user = request("GET", "user", accessToken = saved.accessToken)
            return saved.copy(
                userId = user.optString("id", saved.userId),
                email = user.optString("email", saved.email),
                displayName = user.optJSONObject("user_metadata")
                    ?.optString("full_name")
                    .orEmpty()
                    .ifBlank { saved.displayName }
            ).also(sessionStore::save)
        } catch (error: SupabaseAuthException) {
            if (error.statusCode != 401) throw error
            return refresh(saved.refreshToken)
        }
    }

    fun signOut() {
        val saved = sessionStore.load()
        try {
            if (saved != null) request("POST", "logout", accessToken = saved.accessToken)
        } finally {
            sessionStore.clear()
        }
    }

    private fun refresh(refreshToken: String): AuthSession {
        return authToken(
            "token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken)
        ).also(sessionStore::save)
    }

    private fun authToken(path: String, body: JSONObject): AuthSession =
        request("POST", path, body = body).toSession()

    private fun JSONObject.toSession(): AuthSession {
        val user = optJSONObject("user")
            ?: throw IOException("The authentication response did not include a user.")
        val accessToken = optString("access_token")
        val refreshToken = optString("refresh_token")
        val userId = user.optString("id")
        if (accessToken.isBlank() || refreshToken.isBlank() || userId.isBlank()) {
            throw IOException("The authentication response did not include a complete session.")
        }
        val expiresAt = optLong(
            "expires_at",
            System.currentTimeMillis() / 1000 + optLong("expires_in", 3600)
        )
        val metadata = user.optJSONObject("user_metadata")
        return AuthSession(
            accessToken,
            refreshToken,
            expiresAt,
            userId,
            user.optString("email"),
            metadata?.optString("full_name").orEmpty().ifBlank { metadata?.optString("name").orEmpty() }
        )
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null
    ): JSONObject {
        if (baseUrl.isBlank() || anonKey.isBlank()) {
            throw IOException(
                "Supabase is not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY in frontend/local.properties or the build environment."
            )
        }
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            throw IOException("Supabase URL must use HTTPS.")
        }

        val connection = (URL("$baseUrl/auth/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Accept", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use {
                    it.write(body.toString())
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (status !in 200..299) {
                val code = response.optString("code").ifBlank { response.optString("error_code") }.lowercase()
                val message = when {
                    code == "email_exists" -> "This email already has an account. Sign in instead."
                    status == 400 || status == 401 -> "Email or password is incorrect, or the request needs attention."
                    status == 422 -> "The account could not be created. Check your details and try again."
                    status == 429 -> "Too many attempts. Wait a little before trying again."
                    status >= 500 -> "Sign-in is temporarily unavailable. Try again later."
                    else -> "Authentication could not be completed. Check your details and try again."
                }
                throw SupabaseAuthException(status, message)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}

class SupabaseAuthException(val statusCode: Int, message: String) : IOException(message)

data class AuthCallback(val session: AuthSession, val passwordRecovery: Boolean)

private class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)

    fun save(session: AuthSession) {
        val json = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at", session.expiresAt)
            .put("user_id", session.userId)
            .put("email", session.email)
            .put("display_name", session.displayName)
            .toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(json.toByteArray(StandardCharsets.UTF_8))
        preferences.edit().putString(SESSION_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun load(): AuthSession? {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        require(encrypted.size > IV_LENGTH) { "Saved sign-in session is invalid. Sign in again." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, encrypted, 0, IV_LENGTH))
        val json = JSONObject(String(cipher.doFinal(encrypted, IV_LENGTH, encrypted.size - IV_LENGTH), StandardCharsets.UTF_8))
        return AuthSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = json.getLong("expires_at"),
            userId = json.getString("user_id"),
            email = json.optString("email"),
            displayName = json.optString("display_name")
        )
    }

    fun clear() {
        preferences.edit().remove(SESSION_KEY).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "farm_family_supabase_session"
        const val SESSION_KEY = "encrypted_session"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
