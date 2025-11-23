package com.davidwxcui.waterwise.ui.profile

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/**
 * Auth API placeholder.
 * Replace LocalAuthRepository with your real backend later.
 */
interface AuthApi {
    suspend fun register(ctx: Context, name: String, email: String, password: String): Result<AuthUser>
    suspend fun login(ctx: Context, email: String, password: String): Result<AuthUser>
    suspend fun updateProfile(ctx: Context, uid: String, profile: Profile): Result<Unit>
}

data class AuthUser(val uid: String, val token: String)

/**
 * Local auth implementation (SharedPreferences as a fake DB).
 */
object LocalAuthRepository : AuthApi {

    private const val AUTH_FILE = "profile"
    private const val KEY_UID = "uid"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_LOGGED_IN = "loggedIn"
    private const val KEY_REGISTERED_EMAIL = "registered_email"
    private const val KEY_REGISTERED_PWD_HASH = "registered_pwd_hash"

    override suspend fun register(
        ctx: Context,
        name: String,
        email: String,
        password: String
    ): Result<AuthUser> {
        val sp = ctx.getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)

        val existingEmail = sp.getString(KEY_REGISTERED_EMAIL, null)
        if (!existingEmail.isNullOrBlank() && existingEmail == email) {
            return Result.failure(Exception("Account already exists"))
        }

        val uid = UUID.randomUUID().toString().replace("-", "").take(10)
        val hash = sha256(password)
        val token = "local_token_$uid"

        sp.edit()
            .putString(KEY_UID, uid)
            .putString(KEY_REGISTERED_EMAIL, email)
            .putString(KEY_REGISTERED_PWD_HASH, hash)
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_TOKEN, token)
            .apply()

        return Result.success(AuthUser(uid, token))
    }

    override suspend fun login(
        ctx: Context,
        email: String,
        password: String
    ): Result<AuthUser> {
        val sp = ctx.getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)

        val regEmail = sp.getString(KEY_REGISTERED_EMAIL, null)
        val regHash = sp.getString(KEY_REGISTERED_PWD_HASH, null)
        val uid = sp.getString(KEY_UID, null)

        if (regEmail.isNullOrBlank() || regHash.isNullOrBlank() || uid.isNullOrBlank()) {
            return Result.failure(Exception("No account yet"))
        }

        val inputHash = sha256(password)
        if (email != regEmail || inputHash != regHash) {
            return Result.failure(Exception("Email or password incorrect"))
        }

        val token = "local_token_$uid"
        sp.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_TOKEN, token)
            .apply()

        return Result.success(AuthUser(uid, token))
    }

    override suspend fun updateProfile(ctx: Context, uid: String, profile: Profile): Result<Unit> {
        // Local mode: just save to ProfilePrefs
        ProfilePrefs.save(ctx, profile)
        return Result.success(Unit)
    }

    fun isLoggedIn(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_LOGGED_IN, false) &&
                !sp.getString(KEY_TOKEN, null).isNullOrBlank()
    }

    fun getUid(ctx: Context): String? {
        val sp = ctx.getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)
        return sp.getString(KEY_UID, null)
    }

    fun logout(ctx: Context) {
        val sp = ctx.getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .remove(KEY_TOKEN)
            // Keep uid / registered_email / registered_pwd_hash
            .apply()
    }

    private fun sha256(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
