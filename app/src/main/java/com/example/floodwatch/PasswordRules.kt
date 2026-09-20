package com.example.floodwatch

object PasswordRules {
    const val ERROR_MESSAGE =
        "Password must be at least 12 characters with uppercase, number, and special character (!@#$%^&*)."

    private val strongPassword = Regex("^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#\$%^&*]).{12,}\$")

    fun isValid(password: String): Boolean = strongPassword.matches(password)
}
