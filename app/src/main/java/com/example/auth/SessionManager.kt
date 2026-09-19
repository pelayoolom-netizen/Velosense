package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserAccount?>(loadSession())
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    companion object {
        private const val PREFS_NAME = "velosense_secure_user_session"
        private const val KEY_USER_ID = "session_user_id"
        private const val KEY_GOOGLE_ID = "session_google_id"
        private const val KEY_EMAIL = "session_email"
        private const val KEY_DISPLAY_NAME = "session_display_name"
        private const val KEY_PHOTO_URL = "session_photo_url"
        private const val KEY_CREATED_AT = "session_created_at"
        private const val KEY_LAST_LOGIN = "session_last_login"
        private const val KEY_IS_GUEST = "session_is_guest"
        private const val KEY_HAS_SESSION = "session_has_active"
    }

    private fun loadSession(): UserAccount? {
        val hasActive = prefs.getBoolean(KEY_HAS_SESSION, false)
        if (!hasActive) return null

        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "Ciclista Velo") ?: "Ciclista Velo"
        val email = prefs.getString(KEY_EMAIL, null)
        val googleId = prefs.getString(KEY_GOOGLE_ID, null)
        val photoUrl = prefs.getString(KEY_PHOTO_URL, null)
        val createdAt = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis())
        val lastLogin = prefs.getLong(KEY_LAST_LOGIN, System.currentTimeMillis())
        val isGuest = prefs.getBoolean(KEY_IS_GUEST, false)

        return UserAccount(
            id = id,
            googleAccountId = googleId,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl,
            createdAt = createdAt,
            lastLogin = lastLogin,
            isGuest = isGuest
        )
    }

    fun saveSession(user: UserAccount) {
        prefs.edit()
            .putBoolean(KEY_HAS_SESSION, true)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_GOOGLE_ID, user.googleAccountId)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_DISPLAY_NAME, user.displayName)
            .putString(KEY_PHOTO_URL, user.photoUrl)
            .putLong(KEY_CREATED_AT, user.createdAt)
            .putLong(KEY_LAST_LOGIN, user.lastLogin)
            .putBoolean(KEY_IS_GUEST, user.isGuest)
            .apply()

        _currentUser.value = user
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    fun getActiveUser(): UserAccount? = _currentUser.value

    fun isLoggedIn(): Boolean = _currentUser.value != null
}
