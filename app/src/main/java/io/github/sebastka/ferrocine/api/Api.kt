package io.github.sebastka.ferrocine.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/** An answer other than 2xx; `error` is the API's `error` field. */
class ApiException(val status: Int, val error: String?) : IOException("HTTP $status: ${error ?: "error"}")

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * The core's JSON API at [base] (its public URL), as the device holding
 * [token] (none while pairing).
 */
class Api(val base: HttpUrl, private val http: OkHttpClient, val token: String?) {

    /** An absolute URL for a path the API returned (`/api/v1/…`). */
    fun url(path: String): String = base.resolve(path)?.toString() ?: path

    private fun request(path: String): Request.Builder {
        val b = Request.Builder().url(url(path))
        if (token != null) b.header("Authorization", "Bearer $token")
        return b
    }

    private suspend fun call(req: Request): String = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { resp -> body(resp) }
    }

    private fun body(resp: Response): String {
        val text = resp.body.string()
        if (!resp.isSuccessful) {
            val error = runCatching {
                json.parseToJsonElement(text).let { it as JsonObject }["error"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            throw ApiException(resp.code, error)
        }
        return text
    }

    private suspend inline fun <reified T> get(path: String): T =
        json.decodeFromString(call(request(path).get().build()))

    private suspend inline fun <reified B, reified T> post(path: String, body: B): T =
        json.decodeFromString(call(request(path).post(jsonBody(json.encodeToString(body))).build()))

    private suspend fun send(method: String, path: String) {
        call(request(path).method(method, if (method == "DELETE") null else jsonBody("{}")).build())
    }

    private fun jsonBody(s: String): RequestBody = s.toRequestBody("application/json".toMediaType())

    // Reachability and pairing.

    suspend fun healthy(): Boolean = runCatching { call(request("/healthz").get().build()) == "ok" }.getOrDefault(false)

    suspend fun pair(deviceName: String, platform: String): Pairing = post("/api/v1/pair", PairRequest(deviceName, platform))

    /** The token once approved; `null` while pending. Throws when expired. */
    suspend fun pairToken(secret: String): Granted? = try {
        post<TokenRequest, Granted>("/api/v1/pair/token", TokenRequest(secret))
    } catch (e: ApiException) {
        if (e.status == 400 && e.error == "authorization_pending") null else throw e
    }

    // Signed in.

    suspend fun me(): User = get("/api/v1/me")
    suspend fun devices(): List<Device> = get("/api/v1/me/devices")
    suspend fun signOut(deviceId: Long) = send("DELETE", "/api/v1/me/devices/$deviceId")

    suspend fun home(): Home = get("/api/v1/home")
    suspend fun libraries(): List<Library> = get("/api/v1/libraries")

    suspend fun libraryItems(id: String, sort: String, offset: Int, limit: Int): ItemPage {
        val order = if (sort == "title") "asc" else "desc"
        return get("/api/v1/libraries/${seg(id)}/items?sort=$sort&order=$order&offset=$offset&limit=$limit")
    }

    suspend fun item(id: Long): Item = get("/api/v1/items/$id")
    suspend fun children(id: Long): List<Item> = get("/api/v1/items/$id/children")
    suspend fun search(q: String): List<Item> = get("/api/v1/search?q=${seg(q)}&limit=60")

    suspend fun setFavorite(id: Long, on: Boolean) = send(if (on) "POST" else "DELETE", "/api/v1/items/$id/favorite")
    suspend fun setPlayed(id: Long, on: Boolean) = send(if (on) "POST" else "DELETE", "/api/v1/items/$id/played")

    suspend fun progress(id: Long, positionMs: Long) {
        call(request("/api/v1/items/$id/progress").post(jsonBody(json.encodeToString(Progress(positionMs)))).build())
    }

    suspend fun playback(id: Long, req: PlaybackRequest): Playback = post("/api/v1/items/$id/playback", req)

    suspend fun stopHls(sessionId: String) = runCatching { send("DELETE", "/api/v1/hls/$sessionId") }

    fun subtitleUrl(itemId: Long, index: Int, startMs: Long): String =
        url("/api/v1/items/$itemId/subtitles/$index.vtt?start_ms=$startMs")

    private fun seg(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        /**
         * The server's origin as typed: `https://` added when no scheme is
         * given, any path dropped (Ferrocine serves one origin). `null` when
         * it cannot be a URL.
         */
        fun parseServer(typed: String): HttpUrl? {
            val t = typed.trim()
            if (t.isEmpty() || t.any { it.isWhitespace() }) return null
            val withScheme = if (t.contains("://")) t else "https://$t"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            return url.newBuilder().encodedPath("/").query(null).fragment(null).build()
        }
    }
}
