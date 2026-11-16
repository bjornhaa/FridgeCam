package com.haakenstad.fridgecam.analyse

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

/** Kjører Gemini-leverandøren mot en falsk Interactions-server på localhost. */
class GeminiVisionProviderTest {

    private val mapper = JsonMapper.builder().build()
    private lateinit var server: HttpServer

    private var status = 200
    private var svar = ""
    private var mottattSti: String? = null
    private var mottattBody: JsonNode? = null
    private var mottattNokkel: String? = null
    private var mottattRevisjon: String? = null

    private val bilde = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 9, 8, 7)

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            mottattSti = ex.requestURI.path
            mottattBody = mapper.readTree(ex.requestBody.readAllBytes())
            mottattNokkel = ex.requestHeaders.getFirst("x-goog-api-key")
            mottattRevisjon = ex.requestHeaders.getFirst("Api-Revision")
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

    private fun provider(modell: String = "gemini-4-hydrogen", innsats: Innsats = Innsats.MEDIUM) =
        GeminiVisionProvider("test-nokkel", modell, mapper, innsats, "http://127.0.0.1:${server.address.port}")

    private fun interaksjon(tekst: String, status: String = "completed") = """
        {"id":"v1_1","status":"$status","model":"gemini-4-hydrogen",
         "steps":[
           {"type":"model_output","content":[{"type":"text","text":${mapper.writeValueAsString(tekst)}}]}
         ],
         "usage":{"total_input_tokens":2000,"total_output_tokens":400,"total_thought_tokens":600,"total_tokens":3000}}
    """.trimIndent()

    @Test
    fun `sender bilde, prompt og skjema, og tolker box_2d-bokser`() {
        svar = interaksjon("""{"varer":[{"navn":"brunost","antall":1,"sikkerhet":"høy","bokser":[[200,100,400,300]]}]}""")

        val resultat = provider().analyser(bilde, "image/jpeg")

        // box_2d [ymin, xmin, ymax, xmax] → Boks(x1, y1, x2, y2)
        assertThat(resultat.varer).containsExactly(FunnetVare("brunost", 1, Sikkerhet.HOY, listOf(Boks(100, 200, 300, 400))))
        assertThat(mottattSti).isEqualTo("/v1beta/interactions")
        assertThat(mottattNokkel).isEqualTo("test-nokkel")
        assertThat(mottattRevisjon).isEqualTo(GeminiVisionProvider.API_REVISJON)
        val body = mottattBody!!
        assertThat(body["model"].asString()).isEqualTo("gemini-4-hydrogen")
        assertThat(body["system_instruction"].asString()).isEqualTo(AnalysePrompt.system(Boksformat.BOX_2D))
        assertThat(body["input"][0]["type"].asString()).isEqualTo("image")
        assertThat(body["input"][0]["mime_type"].asString()).isEqualTo("image/jpeg")
        assertThat(body["input"][0]["data"].asString()).isEqualTo(Base64.getEncoder().encodeToString(bilde))
        assertThat(body["input"][1]["text"].asString()).isEqualTo(AnalysePrompt.BRUKER)
        assertThat(body["response_format"]["mime_type"].asString()).isEqualTo("application/json")
        assertThat(body["response_format"]["schema"]["required"][0].asString()).isEqualTo("varer")
        assertThat(body["store"].asBoolean()).isFalse()
        assertThat(body["generation_config"]["thinking_level"].asString()).isEqualTo("medium")
    }

    @Test
    fun `AI_EFFORT styrer thinking_level`() {
        svar = interaksjon("""{"varer":[]}""")

        provider(innsats = Innsats.LOW).analyser(bilde, "image/jpeg")

        assertThat(mottattBody!!["generation_config"]["thinking_level"].asString()).isEqualTo("low")
    }

    @Test
    fun `rapporterer tokenforbruk med tenkning som output`() {
        svar = interaksjon("""{"varer":[]}""")

        val forbruk = provider().analyser(bilde, "image/jpeg").forbruk!!

        // 2000 inn × $1.50 + (400 + 600) ut × $7.50 per million tokens
        assertThat(forbruk).isEqualTo(Tokenforbruk("gemini-4-hydrogen", 2000, 1000, 0.0105))
    }

    @Test
    fun `interaksjon som ikke er fullført gir ai_feil`() {
        svar = interaksjon("", status = "incomplete")

        assertThatThrownBy { provider().analyser(bilde, "image/jpeg") }
            .extracting("code").isEqualTo("ai_feil")
    }

    @Test
    fun `blokkert av sikkerhetsfilter gir ai_avviste`() {
        svar = """{"id":"v1_2","status":"failed","errors":[{"code":"safety_blocked","message":"Blocked for safety"}]}"""

        assertThatThrownBy { provider().analyser(bilde, "image/jpeg") }
            .extracting("code").isEqualTo("ai_avviste")
    }

    @Test
    fun `svar som ikke kan tolkes gir ai_feil`() {
        svar = interaksjon("Jeg ser brunost.")

        assertThatThrownBy { provider().analyser(bilde, "image/jpeg") }
            .extracting("code").isEqualTo("ai_feil")
    }

    @Test
    fun `HTTP-feil oversettes til feilkoder appen forstår`() {
        val tilfeller = mapOf(
            429 to "rate_limit",
            503 to "ai_utilgjengelig",
            500 to "ai_utilgjengelig",
            403 to "ai_feil",
            400 to "ai_feil",
        )
        for ((httpStatus, kode) in tilfeller) {
            status = httpStatus
            svar = """{"error":{"code":$httpStatus,"message":"feil","status":"X"}}"""

            assertThatThrownBy { provider().analyser(bilde, "image/jpeg") }
                .`as`("HTTP $httpStatus")
                .extracting("code").isEqualTo(kode)
        }
    }
}
