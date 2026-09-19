package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.navigation.VeloSenseAppNav
import com.example.ui.theme.VeloSenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep screen on for bike computer functionality
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        handleStravaOAuthIntent(intent)

        setContent {
            VeloSenseTheme {
                VeloSenseAppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStravaOAuthIntent(intent)
    }

    private fun handleStravaOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "velosense" && uri.host == "strava-callback") {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            val app = application as? VeloSenseApplication ?: return

            if (!code.isNullOrBlank()) {
                Log.i("MainActivity", "Recibido código de autorización de Strava: ${code.take(6)}...")
                Toast.makeText(this, "Conectando con Strava...", Toast.LENGTH_SHORT).show()
                app.stravaAuthManager.exchangeCodeForTokenAsync(code) { result ->
                    result.fold(
                        onSuccess = { token ->
                            Toast.makeText(
                                this,
                                "¡Conectado a Strava con éxito como ${token.athleteFullName}!",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onFailure = { ex ->
                            Toast.makeText(
                                this,
                                "Error al autorizar con Strava: ${ex.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            } else if (!error.isNullOrBlank()) {
                Log.w("MainActivity", "Error devuelto por Strava OAuth: $error")
                app.stravaAuthManager.notifyAuthError("Autorización cancelada o denegada por el usuario: $error")
                Toast.makeText(this, "Autorización de Strava cancelada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}


