package com.example.data.network

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// --- Gemini Request & Response Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null,
    @Json(name = "fileData") val fileData: FileData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String // Base64 String
)

@JsonClass(generateAdapter = true)
data class FileData(
    @Json(name = "fileUri") val fileUri: String,
    @Json(name = "mimeType") val mimeType: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "responseMimeType") val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content? = null
)

// --- Files API Models (used for long audio files) ---

data class UploadedGeminiFile(
    val uri: String,
    val mimeType: String
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Retrofit Client Singleton ---

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Long read timeout: transcribing/summarizing a large audio chunk can take several minutes.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(65, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

/**
 * Uploads an audio file to the Gemini Files API and returns a reference (fileUri)
 * that can be sent as `fileData` in a generateContent request.
 *
 * This removes the ~20MB inline `(inline_data)` payload limit, so long recordings
 * (e.g. 1 hour of audio) can be processed in a single request without splitting.
 * Uploaded files are automatically deleted by Google after 48 hours.
 */
object GeminiFileUploader {
    private const val TAG = "GeminiFileUploader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS) // resumable upload of large files
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads [file] and waits until Google finishes processing it.
     * Returns null on failure so the caller can decide on a fallback.
     */
    suspend fun uploadAudio(apiKey: String, file: File, mimeType: String): UploadedGeminiFile? {
        return withContext(Dispatchers.IO) {
            try {
                uploadAudioInternal(apiKey, file, mimeType)
            } catch (e: Exception) {
                Log.e(TAG, "Files API upload failed", e)
                null
            }
        }
    }

    private suspend fun uploadAudioInternal(apiKey: String, file: File, mimeType: String): UploadedGeminiFile? {
        if (!file.exists() || file.length() == 0L) return null

        val startJson = "{\"file\":{\"displayName\":\"voicescript_audio_${System.currentTimeMillis()}\"}}"
        val startRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/upload/v1beta/files")
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", file.length().toString())
            .header("X-Goog-Upload-Header-Content-Type", mimeType)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), startJson))
            .build()

        client.newCall(startRequest).execute().use { startResponse ->
            if (!startResponse.isSuccessful) {
                Log.e(TAG, "Upload init failed: ${startResponse.code} ${startResponse.body?.string().orEmpty()}")
                return null
            }
            val uploadUrl = startResponse.header("X-Goog-Upload-URL") ?: run {
                Log.e(TAG, "Missing X-Goog-Upload-URL header")
                return null
            }

            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .header("Content-Type", mimeType)
                .header("Content-Length", file.length().toString())
                .post(RequestBody.create(mimeType.toMediaTypeOrNull(), file))
                .build()

            client.newCall(uploadRequest).execute().use { uploadResponse ->
                if (!uploadResponse.isSuccessful) {
                    Log.e(TAG, "Upload failed: ${uploadResponse.code} ${uploadResponse.body?.string().orEmpty()}")
                    return null
                }
                val body = uploadResponse.body?.string() ?: return null
                val uri = parseFileUriFromJson(body) ?: run {
                    Log.e(TAG, "No file name in upload finalize response: $body")
                    return null
                }
                return waitUntilActive(apiKey, uri, mimeType)
            }
        }
    }

    /**
     * Polls the file metadata until Google finishes server-side processing
     * (state == ACTIVE). Gives up after ~2 minutes.
     */
    private suspend fun waitUntilActive(apiKey: String, fileUriOrNull: String, mimeType: String): UploadedGeminiFile? {
        // The finalize response contains the resource "name" (e.g. files/abc123),
        // while the actual fileUri used in generateContent is stored in "uri".
        // We poll "name" and return the final "uri".
        val resourceName = fileUriOrNull.substringBefore("?")
        val pollUrl = if (resourceName.startsWith("files/"))
            "https://generativelanguage.googleapis.com/v1beta/$resourceName"
        else return null

        var lastUri = fileUriOrNull
        repeat(40) { attempt ->
            val pollRequest = Request.Builder()
                .url(pollUrl)
                .header("x-goog-api-key", apiKey)
                .get()
                .build()
            try {
                client.newCall(pollRequest).execute().use { pollResponse ->
                    val body = pollResponse.body?.string().orEmpty()
                    if (pollResponse.isSuccessful) {
                        val state = parseJsonField(body, "state")
                        val uri = parseJsonField(body, "uri")
                        if (!uri.isNullOrBlank()) lastUri = uri
                        when {
                            state?.contains("ACTIVE") == true ->
                                return UploadedGeminiFile(fileUri = lastUri, mimeType = mimeType)
                            state?.contains("FAILED") == true -> {
                                Log.e(TAG, "File processing failed: $body")
                                return null
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Poll attempt $attempt failed", e)
            }
            delay(3000)
        }
        Log.e(TAG, "Timed out waiting for file to become ACTIVE")
        return null
    }

    /**
     * Minimal, dependency-free JSON field extraction. Handles the top-level fields
     * of the Files API response objects ({"name": "...", "uri": "...", "state": "..."}).
     */
    private fun parseJsonField(json: String, field: String): String? {
        val key = "\"$field\""
        val keyIndex = json.indexOf(key).takeIf { it >= 0 } ?: return null
        var cursor = json.indexOf(':', keyIndex + key.length) + 1
        while (cursor < json.length && json[cursor].isWhitespace()) cursor++
        if (cursor >= json.length) return null
        if (json[cursor] != '"') return null // only string values are relevant here
        val sb = StringBuilder()
        var i = cursor + 1
        while (i < json.length) {
            val c = json[i]
            if (c == '\\') {
                if (i + 1 < json.length) {
                    sb.append(json[i + 1])
                    i += 2
                    continue
                }
                break
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun parseFileUriFromJson(json: String): String? = parseJsonField(json, "name")
}
