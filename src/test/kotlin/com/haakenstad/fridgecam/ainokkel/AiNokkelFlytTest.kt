package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.AppTest
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.put
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Skanning med ekte Anthropic-leverandør mot en falsk Anthropic-server: at
 * husstandens egen nøkkel er den som sendes, at husstander uten nøkkel stoppes,
 * og at nøkkelen aldri havner i loggen. Egen Spring-kontekst (AI_PROVIDER=anthropic).
 */
@TestPropertySource(properties = ["fridgecam.ai.provider=anthropic"])
@ExtendWith(OutputCaptureExtension::class)
class AiNokkelFlytTest : AppTest() {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)

    private fun skann(token: String) = mvc.multipart("/api/analyze") {
        header("Authorization", "Bearer $token")
        file(MockMultipartFile("bilde", "k.jpg", "image/jpeg", jpeg))
    }

    private fun lagreNokkel(token: String, nokkel: String) {
        mvc.put("/api/husstand/ai/nokkel") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"leverandor":"anthropic","nokkel":"$nokkel"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `uten nøkkel stoppes skanningen før noe sendes til AI-en`() {
        val antallFor = mottatteNokler.size

        skann(registrer()).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("mangler_ai_nokkel") }
        }
        assertThat(mottatteNokler).hasSize(antallFor)
    }

    @Test
    fun `skanningen bruker husstandens egen nøkkel, og den logges aldri`(logg: CapturedOutput) {
        val forste = registrer()
        val andre = registrer()
        lagreNokkel(forste, NOKKEL_EN)
        lagreNokkel(andre, NOKKEL_TO)

        skann(forste).andExpect { status { isOk() }; jsonPath("$.varer[0].navn") { value("melk") } }
        assertThat(mottatteNokler.last()).isEqualTo(NOKKEL_EN)
        skann(andre).andExpect { status { isOk() } }
        assertThat(mottatteNokler.last()).isEqualTo(NOKKEL_TO)

        assertThat(logg.all).contains("Analyse:").doesNotContain(NOKKEL_EN).doesNotContain(NOKKEL_TO)
    }

    @Test
    fun `nøkkel som AI-tjenesten avviser gir egen feilkode`(logg: CapturedOutput) {
        val token = registrer()
        lagreNokkel(token, NOKKEL_AVVIST)

        skann(token).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ugyldig_ai_nokkel") }
        }
        assertThat(mottatteNokler.last()).isEqualTo(NOKKEL_AVVIST)
        assertThat(logg.all).doesNotContain(NOKKEL_AVVIST)
    }

    @Test
    fun `husstandens valgte modell sendes til AI-tjenesten`() {
        val token = registrer()
        lagreNokkel(token, NOKKEL_EN)
        mvc.put("/api/husstand/ai/valg") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"leverandor":"anthropic","modell":"claude-haiku-5-5"}"""
        }.andExpect { status { isOk() } }

        skann(token).andExpect { status { isOk() }; jsonPath("$.leverandor") { value("anthropic/claude-haiku-5-5 (effort medium)") } }
        assertThat(mottatteModeller.last()).isEqualTo("claude-haiku-5-5")
    }

    @Test
    fun `valgt leverandør uten nøkkel stopper skanningen selv om en annen leverandør har nøkkel`() {
        val token = registrer()
        lagreNokkel(token, NOKKEL_EN)
        mvc.put("/api/husstand/ai/valg") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"leverandor":"gemini","modell":"gemini-3.8-flash"}"""
        }.andExpect { status { isOk() } }

        skann(token).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("mangler_ai_nokkel") }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("Google Gemini")) }
        }
    }

    companion object {
        const val NOKKEL_EN = "sk-ant-api03-forste-husstand-testnokkel-1111"
        const val NOKKEL_TO = "sk-ant-api03-andre-husstand-testnokkel-2222"
        const val NOKKEL_AVVIST = "sk-ant-api03-avvist-testnokkel-3333"

        val mottatteNokler = CopyOnWriteArrayList<String>()
        val mottatteModeller = CopyOnWriteArrayList<String>()

        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                val body = String(ex.requestBody.readAllBytes())
                Regex("\"model\"\\s*:\\s*\"([^\"]+)\"").find(body)?.let { mottatteModeller += it.groupValues[1] }
                // SDK-en sender nøkkelen som `Authorization: Bearer …` (eldre versjoner: `x-api-key`).
                val nokkel = ex.requestHeaders.getFirst("x-api-key")
                    ?: ex.requestHeaders.getFirst("Authorization").orEmpty().removePrefix("Bearer ")
                mottatteNokler += nokkel
                val (status, svar) = if (nokkel == NOKKEL_AVVIST) {
                    401 to """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
                } else {
                    200 to """
                        {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                         "content":[{"type":"text","text":"{\"varer\":[{\"navn\":\"melk\",\"antall\":1,\"sikkerhet\":\"høy\"}]}"}],
                         "stop_reason":"end_turn","stop_sequence":null,
                         "usage":{"input_tokens":10,"output_tokens":5}}
                    """.trimIndent()
                }
                val bytes = svar.toByteArray()
                ex.responseHeaders.add("content-type", "application/json")
                ex.sendResponseHeaders(status, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun anthropicServer(registry: DynamicPropertyRegistry) {
            registry.add("fridgecam.ai.anthropic-base-url") { "http://127.0.0.1:${server.address.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stopp() = server.stop(0)
    }
}
