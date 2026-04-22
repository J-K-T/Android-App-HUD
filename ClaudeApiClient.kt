package com.ringhud.hud

import android.util.Log
import com.ringhud.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG     = "ClaudeApiClient"
private const val MODEL   = "claude-sonnet-4-20250514"
private const val MAX_TOK = 1024
private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

@Singleton
class ClaudeApiClient @Inject constructor() {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sends a camera frame (JPEG bytes, base64-encoded) and returns
     * a JSON array of detected objects parseable as [ArObject]s.
     *
     * Prompt instructs Claude to respond with ONLY a JSON array:
     * [{"name":"...","distance":"Xm","x":0-85,"y":0-75,"width":8-30,"height":8-30,"confidence":0.0-1.0}]
     */
    suspend fun detectObjects(jpegBase64: String): List<ArObject> {
        val systemPrompt = """
            You are an AR object detector. Analyse the image and return ONLY a valid JSON array.
            Each object: {"name":"string","distance":"Xm","x":0-85,"y":0-75,"width":8-30,"height":8-30,"confidence":0.0-1.0}
            x,y,width,height are percentages of screen size. Return 2-5 objects max.
            NO other text. NO markdown fences. Just the raw JSON array.
        """.trimIndent()

        val content = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "image")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", jpegBase64)
                })
            })
            put(JSONObject().apply {
                put("type", "text")
                put("text", "Detect objects in this camera image.")
            })
        }

        val body = buildRequestBody(systemPrompt, content)
        val responseText = executeRequest(body) ?: return emptyList()

        return parseArObjects(responseText)
    }

    /**
     * Sends a voice query (with optional context about detected objects)
     * and returns Claude's response as a plain string.
     */
    suspend fun chat(
        query: String,
        history: List<Pair<String, String>>,    // (role, content) pairs
        detectedContext: String = ""
    ): String {
        val systemPrompt = """
            You are a futuristic AR HUD assistant. Be concise — 1-2 sentences max.
            ${if (detectedContext.isNotBlank()) "Currently seeing: $detectedContext." else ""}
            Respond helpfully to the user's query about what they see or want to know.
        """.trimIndent()

        val messages = JSONArray().apply {
            history.forEach { (role, content) ->
                put(JSONObject().apply {
                    put("role", role)
                    put("content", content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", query)
            })
        }

        val bodyJson = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", MAX_TOK)
            put("system", systemPrompt)
            put("messages", messages)
        }

        val body = bodyJson.toString().toRequestBody("application/json".toMediaType())
        return executeRequest(body) ?: "Neural link error — please retry."
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private fun buildRequestBody(system: String, userContent: JSONArray): RequestBody {
        val json = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", MAX_TOK)
            put("system", system)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            })
        }
        return json.toString().toRequestBody("application/json".toMediaType())
    }

    private fun executeRequest(body: RequestBody): String? {
        val apiKey = BuildConfig.CLAUDE_API_KEY
        if (apiKey.isBlank()) {
            Log.e(TAG, "CLAUDE_API_KEY is not set — add claudeApiKey=sk-ant-… to local.properties")
            return null
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API error ${response.code}: ${response.body?.string()}")
                    return null
                }
                val respJson = JSONObject(response.body?.string() ?: return null)
                respJson.getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}")
            null
        }
    }

    private fun parseArObjects(json: String): List<ArObject> = try {
        val array = JSONArray(json.trim())
        (0 until array.length()).mapIndexedNotNull { i, _ ->
            val obj = array.getJSONObject(i)
            ArObject(
                id         = i + 1,
                name       = obj.optString("name", "Unknown"),
                distance   = obj.optString("distance", "?m"),
                confidence = obj.optDouble("confidence", 0.7).toFloat(),
                x          = obj.optDouble("x", 20.0).toFloat(),
                y          = obj.optDouble("y", 30.0).toFloat(),
                width      = obj.optDouble("width", 15.0).toFloat(),
                height     = obj.optDouble("height", 15.0).toFloat()
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse AR objects: ${e.message}\nRaw: $json")
        emptyList()
    }
}
