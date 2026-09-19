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
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest

class GoogleAuth(private val context: Context) {
    private val tag = "GoogleAuth"
    private val credentialManager = CredentialManager.create(context)

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

    private fun findActivity(ctx: Context): Activity? {
        var current: Context? = ctx
        while (current != null) {
            if (current is Activity) return current
            current = if (current is ContextWrapper) current.baseContext else null
        }
        return null
    }

    suspend fun signIn(callerContext: Context): Result<UserAccount> {
        return try {
            val serverClientId = getServerClientId()
            if (serverClientId.isNullOrBlank()) {
                val errorMsg = "Falta configurar GOOGLE_SERVER_CLIENT_ID en Secrets (.env). Debe ser un Web Client ID válido creado en Google Cloud Console."
                Log.w(tag, errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            val activity = findActivity(callerContext) ?: findActivity(context)
            val resolvedContext = activity ?: callerContext

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
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Tipo de credencial no compatible con Google ID (${credential.type})"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(tag, "El usuario canceló el selector de cuenta de Google")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Error durante la autenticación con Google Credential Manager: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(tag, "Error al limpiar estado en CredentialManager", e)
        }
    }

    private fun hashString(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
}

