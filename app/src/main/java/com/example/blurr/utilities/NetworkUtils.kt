package com.example.blurr.utilities

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.pow

/**
 * A generic, reusable function to execute a code block with a retry policy.
 * It uses exponential backoff for delays.
 */
suspend fun <T> withRetry(
    times: Int = 3,
    initialDelay: Long = 1000L, // 1 second
    maxDelay: Long = 8000L,  // 8 seconds
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    for (attempt in 1..times) {
        try {
            // Try to execute the block. If it succeeds, return the result immediately.
            return block()
        } catch (e: IOException) {
            lastException = e
            println("❌ Network error on attempt $attempt: ${e.message}")
        } catch (e: SocketTimeoutException) {
            lastException = e
            println("⏱️ Timeout on attempt $attempt: ${e.message}")
        }

        // If we are not on the last attempt, wait before retrying.
        if (attempt < times) {
            val delayTime = (initialDelay * 2.0.pow(attempt - 1)).toLong().coerceAtMost(maxDelay)
            println("Retrying in ${delayTime}ms...")
            delay(delayTime)
        }
    }
    // If all retries have failed, throw the last captured exception.
    throw lastException ?: Exception("Task failed after $times attempts")
}