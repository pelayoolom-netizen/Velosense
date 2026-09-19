package com.example.auth

data class UserAccount(
    val id: String,
    val googleAccountId: String? = null,
    val email: String? = null,
    val displayName: String = "Ciclista Velo",
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val isGuest: Boolean = false
)
