package com.luminara.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Success or a human-readable failure. Nothing in the UI layer sees an exception. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val message: String, val code: Int = 0) : ApiResult<Nothing>

    val valueOrNull: T? get() = (this as? Ok)?.value
}

object LuminaraApi {

    /** 10.0.2.2 is the host machine as seen from the Android emulator. */
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)   // BOB and translation calls can be slow
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun mediaUrl(path: String?): String? =
        path?.let { if (it.startsWith("http")) it else "$baseUrl$it" }

    private suspend inline fun <reified T> call(
        request: Request,
        crossinline decode: (String) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Err(
                        readableError(response.code, body),
                        response.code,
                    )
                }
                ApiResult.Ok(decode(body))
            }
        } catch (e: IOException) {
            ApiResult.Err(
                "Cannot reach the Luminara backend at $baseUrl. " +
                    "Check it is running, then retry."
            )
        } catch (e: Exception) {
            ApiResult.Err(e.message ?: "Unexpected error talking to the backend")
        }
    }

    private fun readableError(code: Int, body: String): String {
        val detail = runCatching {
            json.parseToJsonElement(body).let { el ->
                el.toString().substringAfter("\"detail\":\"", "").substringBefore("\"")
            }
        }.getOrNull().orEmpty()
        return when {
            detail.isNotBlank() -> detail
            code == 404 -> "Not found on the backend."
            code == 409 -> "This lecture has not finished processing yet."
            else -> "Backend returned HTTP $code"
        }
    }

    private fun get(path: String): Request =
        Request.Builder().url("$baseUrl$path").get().build()

    /** Minimal JSON string escaping so questions may contain quotes and newlines. */
    private fun quoted(value: String): String {
        val sb = StringBuilder(value.length + 16).append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun post(path: String, bodyJson: String = "{}"): Request =
        Request.Builder().url("$baseUrl$path").post(bodyJson.toRequestBody(jsonMedia)).build()

    private fun delete(path: String): Request =
        Request.Builder().url("$baseUrl$path").delete().build()

    // -- endpoints ---------------------------------------------------------

    suspend fun config(): ApiResult<ConfigDto> =
        call(get("/api/config")) { json.decodeFromString(it) }

    suspend fun lectures(): ApiResult<LectureListDto> =
        call(get("/api/lectures")) { json.decodeFromString(it) }

    suspend fun createDemo(language: String, reuse: Boolean): ApiResult<CreateLectureResponse> =
        call(
            post("/api/lectures/demo", """{"language":"$language","reuse":$reuse}"""),
        ) { json.decodeFromString(it) }

    suspend fun process(lectureId: String): ApiResult<StatusDto> =
        call(post("/api/lectures/$lectureId/process")) { json.decodeFromString(it) }

    suspend fun status(lectureId: String): ApiResult<StatusDto> =
        call(get("/api/lectures/$lectureId/status")) { json.decodeFromString(it) }

    suspend fun lecture(lectureId: String, language: String): ApiResult<LectureDto> =
        call(get("/api/lectures/$lectureId?language=$language")) {
            json.decodeFromString(it)
        }

    suspend fun translate(lectureId: String, language: String): ApiResult<TranslateResponseDto> =
        call(
            post("/api/lectures/$lectureId/translate", """{"language":"$language"}"""),
        ) { json.decodeFromString(it) }

    suspend fun ask(
        lectureId: String,
        question: String,
        language: String,
        intent: String? = null,
    ): ApiResult<AskResponseDto> {
        val payload = buildString {
            append("""{"question":${quoted(question)}""")
            append(""","language":"$language"""")
            if (intent != null) append(""","intent":"$intent"""")
            append("}")
        }
        return call(post("/api/lectures/$lectureId/ask", payload)) { json.decodeFromString(it) }
    }

    suspend fun suggestions(lectureId: String, language: String): ApiResult<SuggestionsDto> =
        call(get("/api/lectures/$lectureId/suggestions?language=$language")) {
            json.decodeFromString(it)
        }

    suspend fun chatHistory(lectureId: String): ApiResult<ChatHistoryDto> =
        call(get("/api/lectures/$lectureId/chat")) { json.decodeFromString(it) }

    suspend fun clearChat(lectureId: String): ApiResult<String> =
        call(delete("/api/lectures/$lectureId/chat")) { it }

    // -- P1: script, search, study pack ------------------------------------

    suspend fun script(lectureId: String, language: String): ApiResult<ScriptDto> =
        call(get("/api/lectures/$lectureId/script?language=$language")) {
            json.decodeFromString(it)
        }

    suspend fun search(
        lectureId: String,
        query: String,
        language: String,
    ): ApiResult<SearchResponseDto> =
        call(
            get(
                "/api/lectures/$lectureId/search" +
                    "?q=${URLEncoder.encode(query, "UTF-8")}&language=$language"
            )
        ) { json.decodeFromString(it) }

    fun studyPackUrl(lectureId: String, language: String): String =
        "$baseUrl/api/lectures/$lectureId/export.pdf?language=$language"

    /**
     * Download the study pack to a file. Returns the media type actually served
     * so the caller can tell a real PDF from the HTML fallback.
     */
    suspend fun downloadStudyPack(
        lectureId: String,
        language: String,
        sink: File,
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(studyPackUrl(lectureId, language)).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Err(
                        readableError(response.code, response.body?.string().orEmpty()),
                        response.code,
                    )
                }
                val type = response.header("Content-Type").orEmpty()
                response.body?.byteStream()?.use { input ->
                    sink.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext ApiResult.Err("Empty response from the backend")
                ApiResult.Ok(type)
            }
        } catch (e: IOException) {
            ApiResult.Err("Cannot reach the Luminara backend at $baseUrl.")
        } catch (e: Exception) {
            ApiResult.Err(e.message ?: "Could not download the study pack")
        }
    }
}
