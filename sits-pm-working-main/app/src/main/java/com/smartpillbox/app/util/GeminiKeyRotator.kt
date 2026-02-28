package com.smartpillbox.app.util

import android.content.Context
import android.util.Log
import com.smartpillbox.app.BuildConfig

/**
 * Rotates through multiple Gemini API keys to avoid hitting the
 * 20 RPD (requests per day) limit on any single free-tier project.
 *
 * How it works:
 * - Keeps a list of all configured API keys
 * - Tracks which key was last used (persisted via SharedPreferences)
 * - On each call, picks the next key in round-robin order
 * - If a key gets a 429 (rate limited), automatically tries the next key
 * - If ALL keys are exhausted, returns null
 */
object GeminiKeyRotator {

    private const val TAG = "GeminiKeyRotator"
    private const val PREFS_NAME = "gemini_key_prefs"
    private const val KEY_LAST_INDEX = "last_key_index"

    /**
     * Returns all non-blank API keys configured in BuildConfig.
     */
    fun getAllKeys(): List<String> {
        return listOf(
            BuildConfig.GEMINI_API_KEY_1,
            BuildConfig.GEMINI_API_KEY_2,
            BuildConfig.GEMINI_API_KEY_3
        ).filter { it.isNotBlank() }
    }

    /**
     * Gets the next API key to use (round-robin).
     * Returns null if no keys are configured.
     */
    fun getNextKey(context: Context): String? {
        val keys = getAllKeys()
        if (keys.isEmpty()) return null

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastIndex = prefs.getInt(KEY_LAST_INDEX, -1)
        val nextIndex = (lastIndex + 1) % keys.size

        prefs.edit().putInt(KEY_LAST_INDEX, nextIndex).apply()
        Log.d(TAG, "Using key #${nextIndex + 1} of ${keys.size}: ${keys[nextIndex].take(12)}...")

        return keys[nextIndex]
    }

    /**
     * Gets all keys starting from the next one in rotation.
     * This allows trying all keys if the first one fails with 429.
     * Returns the keys in the order they should be tried.
     */
    fun getKeysInRotationOrder(context: Context): List<String> {
        val keys = getAllKeys()
        if (keys.isEmpty()) return emptyList()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastIndex = prefs.getInt(KEY_LAST_INDEX, -1)
        val startIndex = (lastIndex + 1) % keys.size

        // Build a list starting from startIndex, wrapping around
        val ordered = mutableListOf<String>()
        for (i in keys.indices) {
            ordered.add(keys[(startIndex + i) % keys.size])
        }

        // Update the stored index to the first key we'll try
        prefs.edit().putInt(KEY_LAST_INDEX, startIndex).apply()
        Log.d(TAG, "Rotation order: starting at key #${startIndex + 1}, ${keys.size} keys total")

        return ordered
    }

    /**
     * Returns a summary string for debug logging.
     */
    fun getDebugInfo(context: Context): String {
        val keys = getAllKeys()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastIndex = prefs.getInt(KEY_LAST_INDEX, -1)
        return buildString {
            append("API Keys configured: ${keys.size}\n")
            keys.forEachIndexed { i, key ->
                val marker = if (i == lastIndex) " ← last used" else ""
                append("  Key #${i + 1}: ${key.take(12)}...${key.takeLast(4)}$marker\n")
            }
        }
    }
}