package dev.rizukirr.audx.samples.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** POSTs [wavFile] to `<serverUrl>/denoise` and returns the server's reply body. */
suspend fun upload(serverUrl: String, wavFile: File): String = withContext(Dispatchers.IO) {
    HttpClient(CIO).use { client ->
        val response = client.post(serverUrl.trimEnd('/') + "/denoise") {
            contentType(ContentType("audio", "wav"))
            setBody(wavFile.readBytes())
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}: $body")
        body
    }
}
