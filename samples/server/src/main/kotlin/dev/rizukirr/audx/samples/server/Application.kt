package dev.rizukirr.audx.samples.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class DenoiseResponse(val savedAs: String, val frames: Int)

private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module(recordingsDir: File = File("recordings")) {
    install(CallLogging)
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respondText("ok") }

        post("/denoise") {
            val pcm = try {
                parseWav(call.receive<ByteArray>())
            } catch (e: WavFormatException) {
                call.respondText(e.message ?: "invalid wav", status = HttpStatusCode.BadRequest)
                return@post
            }
            // Audx failures (IllegalStateException) and IO errors propagate and
            // become 500s via Ktor's default exception handling, which logs them.
            val denoised = denoise(pcm.sampleRate, pcm.samples)
            recordingsDir.mkdirs()
            val file = File(recordingsDir, "denoised-${LocalDateTime.now().format(timestampFormat)}.wav")
            writeWav(file, pcm.sampleRate, denoised.samples)
            call.application.log.info("saved ${file.absolutePath}")
            call.respond(DenoiseResponse(savedAs = file.name, frames = denoised.frames))
        }
    }
}
