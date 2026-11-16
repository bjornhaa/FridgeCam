package com.haakenstad.fridgecam.analyse

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.haakenstad.fridgecam.common.ApiException
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.util.Base64

/**
 * Kjører leverandøren mot en falsk Anthropic-server på localhost, så vi ser
 * nøyaktig hva som sendes og hvordan svar og feil håndteres, uten ekte API-kall.
 */
class AnthropicVisionProviderTest {

    private val mapper = JsonMapper.builder().build()
    private lateinit var server: HttpServer

    private var status = 200
    private var svar = ""
    private var mottattBody: JsonNode? = null
    private var mottattBeta: String? = null

    private val bilde = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            mottattBody = mapper.readTree(ex.requestBody.readAllBytes())
            mottattBeta = ex.requestHeaders.getFirst("anthropic-beta")
            val bytes = svar.toByteArray()
            ex.responseHeaders.add("content-type", "application/json")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stopp() {
        server.stop(0)
    }

    private fun provider(modell: String = "claude-opus-5-5") = AnthropicVisionProvider(
        AnthropicOkHttpClient.builder()
            .apiKey("sk-ant-test")
            .baseUrl("http://127.0.0.1:${server.address.port}")
            .maxRetries(0)
            .build(),
        modell,
        mapper,
    )

    private fun melding(tekst: String, stopReason: String = "end_turn") = """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
         "content":[{"type":"thinking","thinking":"","signature":"x"},{"type":"text","text":${mapper.writeValueAsString(tekst)}}],
         "stop_reason":"$stopReason","stop_sequence":null,
         "usage":{"input_tokens":1600,"output_tokens":120}}
    """.trimIndent()

    @Test
    fun `sender bilde, prompt og skjema, og tolker svaret`() {
        svar = melding("""{"varer":[{"navn":"melk","antall":1,"sikkerhet":"høy"}]}""")

        val resultat = provider().analyser(bilde, "image/jpeg")

        assertThat(resultat.varer).containsExactly(FunnetVare("melk", 1, Sikkerhet.HOY))
        val body = mottattBody!!
        assertThat(body["model"].asString()).isEqualTo("claude-opus-5-5")
        assertThat(body["system"].asString()).isEqualTo(AnalysePrompt.SYSTEM)
        val innhold = body["messages"][0]["content"]
        assertThat(innhold[0]["type"].asString()).isEqualTo("image")
        assertThat(innhold[0]["source"]["media_type"].asString()).isEqualTo("image/jpeg")
        assertThat(innhold[0]["source"]["data"].asString()).isEqualTo(Base64.getEncoder().encodeToString(bilde))
        assertThat(innhold[1]["text"].asString()).isEqualTo(AnalysePrompt.BRUKER)
        assertThat(body["output_config"]["effort"].asString()).isEqualTo("medium")
        val skjema = body["output_config"]["format"]["schema"]
        assertThat(skjema["required"][0].asString()).isEqualTo("varer")
        assertThat(skjema["properties"]["varer"]["items"]["properties"].has("bokser")).isTrue()
    }

    @Test
    fun `rapporterer tokenforbruk og anslått kostnad`() {
        svar = melding("""{"varer":[]}""")

        val forbruk = provider().analyser(bilde, "image/jpeg").forbruk!!

        // 1600 inn × $4 + 120 ut × $20 per million tokens
        assertThat(forbruk).isEqualTo(Tokenforbruk("claude-opus-5-5", 1600, 120, 0.0088))
    }

    @Test
    fun `ukjent modell gir forbruk uten kostnad`() {
        svar = melding("""{"varer":[]}""").replace("\"model\":\"claude-opus-5-5\"", "\"model\":\"claude-ny-modell\"")

        val forbruk = provider().analyser(bilde, "image/jpeg").forbruk!!

        assertThat(forbruk.modell).isEqualTo("claude-ny-modell")
        assertThat(forbruk.kostnadUsd).isNull()
    }

    @Test
    fun `slår på server-side fallback for modeller som støtter det`() {
        svar = melding("""{"varer":[]}""")

        provider("claude-opus-5-5").analyser(bilde, "image/jpeg")

        assertThat(mottattBody!!["fallbacks"].asString()).isEqualTo("default")
        assertThat(mottattBeta).contains("server-side-fallback-2026-07-01")
    }

    @Test
    fun `sender ikke fallbacks for modeller uten støtte`() {
        svar = melding("""{"varer":[]}""")

        provider("claude-haiku-5-5").analyser(bilde, "image/jpeg")

        assertThat(mottattBody!!.has("fallbacks")).isFalse()
    }

    @Test
    fun `avvist bilde gir ai_avviste`() {
        svar = melding("", stopReason = "refusal")

        assertThatThrownBy { provider().analyser(bilde, "image/jpeg") }
            .isInstanceOf(ApiException::class.java)
            .extracting("code").isEqualTo("ai_avviste")
    }

    @Test
    fun `svar som ikke kan tolkes gir ai_feil`() {
        svar = melding("Jeg ser melk og egg.")

        assertThatThrownBy { provider().analyser(bilde, "image/jpeg") }
            .extracting("code").isEqualTo("ai_feil")
    }

    @Test
    fun `HTTP-feil oversettes til feilkoder appen forstår`() {
        val tilfeller = mapOf(
            429 to "rate_limit",
            529 to "ai_utilgjengelig",
            503 to "ai_utilgjengelig",
            401 to "ai_feil",
            400 to "ai_feil",
        )
        for ((httpStatus, kode) in tilfeller) {
            status = httpStatus
            svar = """{"type":"error","error":{"type":"some_error","message":"feil $httpStatus"}}"""

            assertThatThrownBy { provider().analyser(bilde, "image/jpeg") }
                .`as`("HTTP $httpStatus")
                .extracting("code").isEqualTo(kode)
        }
    }
}
