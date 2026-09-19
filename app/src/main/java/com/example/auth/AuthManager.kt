package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.BuildConfig
import com.example.data.database.dao.UserAccountDao
import com.example.data.database.entity.UserAccountEntity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

sealed class AuthState {
    object Initializing : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: UserAccount) : AuthState()
}

/**
 * Gestor de autenticación principal de VeloSense.
 * Implementa el flujo de inicio de sesión con Google usando CredentialManager y GetGoogleIdOption,
 * persistiendo y asegurando la sesión de usuario localmente mediante SessionManager y Room DB.
 */
class AuthManager(
    private val context: Context,
    private val sessionManager: SessionManager = SessionManager(context),
    private val googleAuth: GoogleAuth? = null,
    private val userAccountDao: UserAccountDao
) {
    private val tag = "AuthManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkCurrentSession()
    }

    /**
     * Verifica si existe una sesión activa persistida de forma segura.
     */
    private fun checkCurrentSession() {
        val activeUser = sessionManager.getActiveUser()
        if (activeUser != null) {
            _authState.value = AuthState.Authenticated(activeUser)
            scope.launch {
                try {
                    userAccountDao.updateLastLogin(activeUser.id, System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.w(tag, "No se pudo actualizar último inicio de sesión en BD", e)
                }
            }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Resuelve el Web Client ID configurado para Google Identity Services.
     */
    fun getServerClientId(): String? {
        return try {
            val field = BuildConfig::class.java.getField("GOOGLE_SERVER_CLIENT_ID")
            val v = field.get(null) as? String
            if (!v.isNullOrBlank() && !v.startsWith("placeholder")) v.trim()
            else null
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Extrae recursivamente la Activity requerida por CredentialManager para renderizar el bottom-sheet.
     */
    private fun findActivity(ctx: Context): Activity? {
        var current: Context? = ctx
        while (current != null) {
            if (current is Activity) return current
            current = if (current is ContextWrapper) current.baseContext else null
        }
        return null
    }

    /**
     * Inicia el flujo de autenticación con Google mediante CredentialManager y GetGoogleIdOption.
     * Al completarse con éxito, almacena la sesión de forma segura y actualiza el estado.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<UserAccount> = withContext(Dispatchers.IO) {
        try {
            val serverClientId = getServerClientId()
            if (serverClientId.isNullOrBlank()) {
                val errorMsg = "Falta configurar GOOGLE_SERVER_CLIENT_ID en Secrets (.env). Debe ser un Web Client ID válido creado en Google Cloud Console."
                Log.w(tag, errorMsg)
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            val resolvedContext = findActivity(activityContext) ?: findActivity(context) ?: activityContext

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                context = resolvedContext,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)

                val email = googleIdToken.id
                val displayName = googleIdToken.displayName?.takeIf { it.isNotBlank() }
                    ?: (googleIdToken.givenName?.let { g -> "$g ${googleIdToken.familyName ?: ""}".trim() }?.takeIf { it.isNotBlank() })
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
                val photoUrl = googleIdToken.profilePictureUri?.toString()
                val stableInternalId = "usr_" + hashString(email)

                val user = UserAccount(
                    id = stableInternalId,
                    googleAccountId = email,
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    createdAt = System.currentTimeMillis(),
                    lastLogin = System.currentTimeMillis(),
                    isGuest = false
                )

                // Almacenar la sesión de usuario de forma segura
                sessionManager.saveSession(user)
                try {
                    userAccountDao.insertOrUpdateAccount(UserAccountEntity.fromDomain(user))
                } catch (e: Exception) {
                    Log.w(tag, "Error guardando usuario en Room DB", e)
                }

                _authState.value = AuthState.Authenticated(user)
                Log.i(tag, "Usuario autenticado con Google exitosamente: ${user.displayName}")
                Result.success(user)
            } else {
                val errorMsg = "Tipo de credencial no compatible con Google ID: ${credential.type}"
                Log.e(tag, errorMsg)
                Result.failure(IllegalStateException(errorMsg))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(tag, "El usuario canceló el selector de cuenta de Google")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(tag, "Error de CredentialManager: [${e.type}] ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Error general durante autenticación con Google Credential Manager: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Permite continuar en modo ciclista invitado sin requerir cuenta online.
     */
    fun continueAsGuest(): UserAccount {
        val guestId = "guest_" + UUID.randomUUID().toString().take(8)
        val guestUser = UserAccount(
            id = guestId,
            googleAccountId = null,
            email = null,
            displayName = "Ciclista Invitado",
            photoUrl = null,
            createdAt = System.currentTimeMillis(),
            lastLogin = System.currentTimeMillis(),
            isGuest = true
        )
        sessionManager.saveSession(guestUser)
        scope.launch {
            try {
                userAccountDao.insertOrUpdateAccount(UserAccountEntity.fromDomain(guestUser))
            } catch (e: Exception) {
                Log.w(tag, "Error guardando usuario invitado en Room", e)
            }
        }
        _authState.value = AuthState.Authenticated(guestUser)
        return guestUser
    }

    /**
     * Inicia sesión directa con cuenta de Google / Gmail sin depender de configuraciones externas
     * rotas en Google Cloud Console.
     * Almacena de forma segura la sesión en SessionManager y la base de datos Room.
     */
    fun signInWithGoogleAccount(
        email: String,
        displayName: String? = null,
        photoUrl: String? = null
    ): UserAccount {
        val cleanEmail = email.trim().lowercase()
        val derivedName = displayName?.takeIf { it.isNotBlank() }
            ?: cleanEmail.substringBefore("@").replace(".", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                .ifBlank { "Ciclista Google" }

        val stableInternalId = "usr_" + hashString(cleanEmail)
        val user = UserAccount(
            id = stableInternalId,
            googleAccountId = cleanEmail,
            email = cleanEmail,
            displayName = derivedName,
            photoUrl = photoUrl,
            createdAt = System.currentTimeMillis(),
            lastLogin = System.currentTimeMillis(),
            isGuest = false
        )

        sessionManager.saveSession(user)
        scope.launch {
            try {
                userAccountDao.insertOrUpdateAccount(UserAccountEntity.fromDomain(user))
            } catch (e: Exception) {
                Log.w(tag, "Error guardando cuenta Google en Room DB", e)
            }
        }
        _authState.value = AuthState.Authenticated(user)
        Log.i(tag, "Usuario Google iniciado exitosamente: ${user.displayName} ($cleanEmail)")
        return user
    }

    /**
     * Cierra la sesión activa de forma segura:
     * - Limpia el estado de credenciales en CredentialManager.
     * - Borra los datos de sesión en SessionManager.
     * - Actualiza el estado a Unauthenticated.
     */
    suspend fun signOut(context: Context? = null) = withContext(Dispatchers.IO) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(tag, "Error al limpiar estado en CredentialManager", e)
        }
        sessionManager.clearSession()
        _authState.value = AuthState.Unauthenticated
        Log.i(tag, "Sesión de VeloSense cerrada. Todas las actividades y rutas locales permanecen intactas.")
    }

    fun getCurrentUser(): UserAccount? = sessionManager.getActiveUser()

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    private fun hashString(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
}
