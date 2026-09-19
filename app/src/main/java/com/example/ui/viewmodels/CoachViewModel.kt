package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VeloSenseApplication
import com.example.coach.CoachManager
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isFromCoach: Boolean,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class CoachViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VeloSenseApplication
    private val rideRepository = app.rideRepository
    private val userRepository = app.userRepository
    private val coachManager = CoachManager()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                isFromCoach = true,
                content = "Hola. Soy VeloSense Coach, tu director deportivo y analista técnico de rendimiento. Analizo tus datos reales de GPS, velocidad, desnivel, vatios estimados y cadencia para ayudarte a optimizar tu rendimiento y planificar tus salidas. ¿En qué trabajamos hoy?"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    val quickQuestions = listOf(
        "Analiza mi última salida",
        "¿He mejorado?",
        "¿Qué debería entrenar mañana?",
        "¿Cómo puedo mejorar en MTB?",
        "¿Cómo afecta el viento a mi rendimiento?",
        "Explícame mis datos de potencia estimada"
    )

    fun sendMessage(query: String) {
        if (query.isBlank() || _isThinking.value) return

        val userMsg = ChatMessage(isFromCoach = false, content = query.trim())
        _messages.update { it + userMsg }
        _isThinking.value = true

        viewModelScope.launch {
            val latest = rideRepository.latestRide.firstOrNull()
            val recent = rideRepository.allRides.firstOrNull() ?: emptyList()
            val profile = userRepository.getProfileDirect()

            val answer = coachManager.consultCoach(
                userMessage = query,
                latestRide = latest,
                recentRides = recent,
                userProfile = profile
            )

            val coachMsg = ChatMessage(isFromCoach = true, content = answer)
            _messages.update { it + coachMsg }
            _isThinking.value = false
        }
    }
}
