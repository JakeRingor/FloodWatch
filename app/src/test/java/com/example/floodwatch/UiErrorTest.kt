package com.example.floodwatch

import com.example.floodwatch.util.UiError
import com.example.floodwatch.util.toUiError
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class UiErrorTest {
    @Test
    fun unknownHostMapsToOffline() {
        assertEquals(UiError.OFFLINE, UnknownHostException().toUiError())
    }

    @Test
    fun socketTimeoutMapsToTimeout() {
        assertEquals(UiError.TIMEOUT, SocketTimeoutException().toUiError())
    }

    @Test
    fun authorizationFailureMapsToUnauthorized() {
        assertEquals(UiError.UNAUTHORIZED, IllegalStateException("HTTP 401").toUiError())
    }
}
