package dev.rizukirr.audx.samples.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `health returns ok`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun `denoise saves wav and reports frames`() = testApplication {
        val dir = createTempDirectory("recordings").toFile()
        application { module(recordingsDir = dir) }

        val samples = ShortArray(1_600) { (it % 128).toShort() } // 100 ms at 16 kHz
        val response = client.post("/denoise") { setBody(wavBytes(16_000, samples)) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"savedAs\""))
        val saved = dir.listFiles()!!.single()
        assertTrue(saved.name.startsWith("denoised-") && saved.name.endsWith(".wav"))
        val pcm = parseWav(saved.readBytes())
        assertEquals(16_000, pcm.sampleRate)
        assertEquals(samples.size, pcm.samples.size)
    }

    @Test
    fun `rejects invalid wav`() = testApplication {
        application { module() }
        val response = client.post("/denoise") { setBody(byteArrayOf(1, 2, 3)) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
