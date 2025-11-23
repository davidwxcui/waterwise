package com.davidwxcui.waterwise.ui.profile

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest
import java.util.UUID
import java.util.UUID.randomUUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Auth API placeholder.
 */
interface AuthApi {
    suspend fun register(ctx: Context, name: String, email: String, password: String): Result<AuthUser>
    suspend fun login(ctx: Context, email: String, password: String): Result<AuthUser>
    suspend fun updateProfile(ctx: Context, uid: String, profile: Profile): Result<Unit>
}

data class AuthUser(val uid: String, val token: String)

/**
 * Firebase auth implementation
 */
object FirebaseAuthRepository : AuthApi {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override suspend fun register(
        ctx: Context,
        name: String,
        email: String,
        password: String
    ): Result<AuthUser> {
        return try {
            val user = createUser(email, password)
            val uid = user.uid
            val token = user.fetchIdToken(false)

            val local = ProfilePrefs.load(ctx).copy(name = name, email = email)
            ProfilePrefs.save(ctx, local)

            upsertUserProfile(uid, local)

            Result.success(AuthUser(uid, token))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(
        ctx: Context,
        email: String,
        password: String
    ): Result<AuthUser> {
        return try {
            val user = signIn(email, password)
            val uid = user.uid
            val token = user.fetchIdToken(false)

            // Make sure Firestore has user file
            val local = ProfilePrefs.load(ctx).copy(email = email)
            ProfilePrefs.save(ctx, local)
            upsertUserProfile(uid, local)

            Result.success(AuthUser(uid, token))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(
        ctx: Context,
        uid: String,
        profile: Profile
    ): Result<Unit> {
        return try {
            // local copy
            ProfilePrefs.save(ctx, profile)

            // Firestore update
            upsertUserProfile(uid, profile)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun currentUid(): String? = auth.currentUser?.uid

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun logout() {
        auth.signOut()
    }

    // ---------- Firestore helpers ----------

    private suspend fun upsertUserProfile(uid: String, pf: Profile) {
        val data = hashMapOf(
            "uid" to uid,
            "name" to pf.name,
            "email" to pf.email,
            "age" to pf.age,
            "sex" to pf.sex.name,
            "heightCm" to pf.heightCm,
            "weightKg" to pf.weightKg,
            "activityLevel" to pf.activity.name,
            "activityFreqLabel" to pf.activityFreqLabel,
            "avatarUri" to pf.avatarUri,
            "roomId" to null
        )

        suspendCoroutine<Unit> { cont ->
            db.collection("users").document(uid)
                .set(data)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    // ---------- Firebase Auth suspend wrappers ----------

    private suspend fun createUser(email: String, password: String): FirebaseUser {
        return suspendCoroutine { cont ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { res ->
                    val u = res.user
                    if (u != null) cont.resume(u)
                    else cont.resumeWithException(Exception("Firebase user is null"))
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    private suspend fun signIn(email: String, password: String): FirebaseUser {
        return suspendCoroutine { cont ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { res ->
                    val u = res.user
                    if (u != null) cont.resume(u)
                    else cont.resumeWithException(Exception("Firebase user is null"))
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    private suspend fun FirebaseUser.fetchIdToken(forceRefresh: Boolean): String {
        return suspendCoroutine { cont ->
            this.getIdToken(forceRefresh)
                .addOnSuccessListener { r ->
                    val t = r.token
                    if (t != null) cont.resume(t)
                    else cont.resumeWithException(Exception("Token is null"))
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }
}

/**
 * Local auth implementation
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

        val uid = randomUUID().toString().replace("-", "").take(10)
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
