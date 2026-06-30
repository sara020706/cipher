package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    suspend fun generateSecureResponse(
        botName: String,
        botPersona: String,
        chatHistory: List<Pair<String, String>> // Pair of (senderId, text) where "ME" represents the user
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is missing or is placeholder.")
            return@withContext "🔐 [SECURE DECRYPTION ERROR] Secure bot key not set. Please provide a valid GEMINI_API_KEY in the AI Studio Secrets Panel to chat with E2EE AI bots."
        }

        try {
            // Build the system instructions
            val systemInstruction = "You are $botName, a secure contact in a highly encrypted, private chat application called CipherChat. " +
                    "Your personality: $botPersona. " +
                    "Crucial: You must write in standard, engaging conversational text. Since this is an E2EE chat, you can occasionally acknowledge that your messages are encrypted on Alice's device and decrypted on your end. But otherwise behave like a real human or highly specialized secure companion, replying briefly as is normal in chat applications. Do not write markdown blocks or overly long paragraphs unless asked."

            // Construct contents array
            val contentsArray = JSONArray()

            // Add history
            for (turn in chatHistory) {
                val role = if (turn.first == "ME") "user" else "model"
                val partObj = JSONObject().put("text", turn.second)
                val partsArray = JSONArray().put(partObj)
                val turnObj = JSONObject()
                    .put("role", role)
                    .put("parts", partsArray)
                contentsArray.put(turnObj)
            }

            // Construct main request JSON
            val requestJson = JSONObject()
                .put("contents", contentsArray)
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val url = "$BASE_URL?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Empty body"
                    Log.e(TAG, "HTTP Error: ${response.code} - $errorBody")
                    return@withContext "🔒 [CIPHER DECRYPTION ERROR] Server returned: ${response.code}. Ensure your Gemini API Key is configured correctly in Secrets."
                }

                val responseBody = response.body?.string() ?: return@withContext "🔒 [ERROR] Received empty response from E2EE node."
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No response text")
                    }
                }
                "🔒 [ERROR] Secure connection was interrupted. Please retry."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generation", e)
            "🔒 [SECURE CONNECTION FAIL] ${e.localizedMessage ?: "Network error"}"
        }
    }
}
