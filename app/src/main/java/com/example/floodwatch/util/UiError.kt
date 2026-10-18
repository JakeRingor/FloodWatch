package com.example.floodwatch.util

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class UiError {
    OFFLINE,
    TIMEOUT,
    UNAUTHORIZED,
    SERVER,
    UNKNOWN
}

fun Throwable.toUiError(): UiError {
    val details = buildString {
        append(this@toUiError::class.java.name)
        append(' ')
        append(message.orEmpty())
    }.lowercase()
    return when {
        this is SocketTimeoutException || details.contains("timeout") -> UiError.TIMEOUT
        this is UnknownHostException || this is IOException &&
            (details.contains("network") || details.contains("connect")) -> UiError.OFFLINE
        details.contains("401") || details.contains("jwt") ||
            details.contains("unauthorized") -> UiError.UNAUTHORIZED
        details.contains("500") || details.contains("502") ||
            details.contains("503") || details.contains("server") -> UiError.SERVER
        else -> UiError.UNKNOWN
    }
}
