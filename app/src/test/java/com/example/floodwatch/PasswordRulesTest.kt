package com.example.floodwatch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {
    @Test
    fun acceptsStrongPassword() {
        assertTrue(PasswordRules.isValid("FloodWatch#2026"))
    }

    @Test
    fun rejectsShortPassword() {
        assertFalse(PasswordRules.isValid("Flood#1"))
    }

    @Test
    fun rejectsPasswordWithoutUppercaseNumberOrSpecialCharacter() {
        assertFalse(PasswordRules.isValid("floodwatch#2026"))
        assertFalse(PasswordRules.isValid("FloodWatchOnly#"))
        assertFalse(PasswordRules.isValid("FloodWatch2026"))
    }
}
