package io.github.sebastka.ferrocine

import io.github.sebastka.ferrocine.api.Api
import io.github.sebastka.ferrocine.api.ApiException
import io.github.sebastka.ferrocine.api.Capabilities
import io.github.sebastka.ferrocine.api.PlaybackRequest
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiTest {
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun api(token: String? = "tok") = Api(server.url("/"), OkHttpClient(), token)

    @Test
    fun parsesServerUrls() {
        assertEquals("https://media.example.com/", Api.parseServer(" media.example.com ")?.toString())
        assertEquals("http://10.0.0.5:8080/", Api.parseServer("http://10.0.0.5:8080/web/#/home")?.toString())
        assertNull(Api.parseServer(""))
        assertNull(Api.parseServer("not a url"))
    }

    @Test
    fun pairsAndPolls() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"code":"K7TQ-M2XD","secret":"s3","approve_url":"https://m/pair?code=K7TQM2XD",
                   "expires_in":600,"interval":5}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"authorization_pending"}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"t","token_type":"Bearer","device":{"id":7,"name":"TV","platform":"androidtv",
                   "created_at":1,"last_seen_at":1},"user":{"id":1,"username":"alice","admin":true,"email":null}}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"expired_token"}"""))
        val a = api(token = null)
        val p = a.pair("TV", "androidtv")
        assertEquals("K7TQ-M2XD", p.code)
        val req = server.takeRequest()
        assertEquals("/api/v1/pair", req.path)
        assertNull(req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains("\"device_name\":\"TV\""))
        assertNull(a.pairToken("s3"))
        val g = a.pairToken("s3")!!
        assertEquals(Triple("t", 7L, "alice"), Triple(g.accessToken, g.device.id, g.user.username))
        try {
            a.pairToken("s3")
            fail("expired")
        } catch (e: ApiException) {
            assertEquals("expired_token", e.error)
        }
    }

    @Test
    fun sendsTheTokenAndCapabilities() = runTest {
        server.enqueue(MockResponse().setBody("""{"method":"direct","url":"/api/v1/items/3/stream","start_ms":0}"""))
        val caps = Capabilities(listOf("mp4", "mkv"), listOf("h264", "hevc"), listOf("aac"), listOf("hdr10"))
        val p = api().playback(3, PlaybackRequest(startMs = 5000, capabilities = caps))
        assertEquals(false, p.hls)
        val req = server.takeRequest()
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body, body.contains("\"selects_tracks\":true"))
        assertTrue(body, body.contains("\"hdr\":[\"hdr10\"]"))
        assertTrue(body, body.contains("\"start_ms\":5000"))
    }

    @Test
    fun ignoresUnknownFields() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":12,"kind":"episode","title":"Pilot","season_number":1,"episode_number":2,"new_field":[1],
                   "images":{"poster":null},"user":{"position_ms":5,"played":false,"favorite":true,"play_count":0}}""",
            ),
        )
        val item = api().item(12)
        assertEquals("S1 · E2", item.subtitle)
        assertEquals(true, item.user?.favorite)
    }
}
