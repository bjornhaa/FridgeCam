package com.haakenstad.fridgecam.analyse

import com.haakenstad.fridgecam.common.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration
import java.util.Base64

/**
 * Google Gemini via Interactions-API-et (#10), kalt direkte over REST.
 *
 * Gemini er trent på bokser i `box_2d`-format, så den får det formatet
 * ([Boksformat.BOX_2D]); varelisten er ellers lik Anthropic sin.
 *
 * Gratisnivået koster ingenting, men Google kan bruke innholdet til å forbedre
 * tjenestene sine. `store: false` ber om at forespørselen ikke lagres for senere
 * henting via API-et.
 */
class GeminiVisionProvider(
    private val apiNokkel: String,
    private val modell: String,
    private val mapper: JsonMapper,
    private val innsats: Innsats = Innsats.MEDIUM,
    baseUrl: String = STANDARD_BASE_URL,
    timeout: Duration = Duration.ofSeconds(90),
) : VisionProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val navn = "gemini/$modell (thinking ${innsats.name.lowercase()})"

    private val http = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build())
                .apply { setReadTimeout(timeout) },
        )
        .build()

    override fun analyser(bilde: ByteArray, mimeType: String): Analyseresultat {
        val svar = try {
            http.post()
                .uri("/v1beta/interactions")
                .header("x-goog-api-key", apiNokkel)
                .header("Api-Revision", API_REVISJON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(foresporsel(bilde, mimeType)))
                .retrieve()
                .body(String::class.java)
                ?.let { mapper.readTree(it) }
                ?: throw aiFeil("tomt svar")
        } catch (e: RestClientResponseException) {
            throw oversett(e)
        } catch (e: ResourceAccessException) {
            log.warn("Fikk ikke kontakt med Gemini", e)
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_utilgjengelig", "Fikk ikke kontakt med AI-tjenesten.")
        }
        return Analyseresultat(tolkSvar(svar), forbruk(svar))
    }

    private fun foresporsel(bilde: ByteArray, mimeType: String) = mapOf(
        "model" to modell,
        "system_instruction" to AnalysePrompt.system(Boksformat.BOX_2D),
        "input" to listOf(
            mapOf("type" to "image", "data" to Base64.getEncoder().encodeToString(bilde), "mime_type" to mimeType),
            mapOf("type" to "text", "text" to AnalysePrompt.BRUKER),
        ),
        "response_format" to mapOf(
            "type" to "text",
            "mime_type" to "application/json",
            "schema" to AnalysePrompt.skjema(Boksformat.BOX_2D),
        ),
        "generation_config" to mapOf(
            "max_output_tokens" to 16_000,
            // Modellens standard er high; vi setter det eksplisitt fra AI_EFFORT.
            "thinking_level" to innsats.name.lowercase(),
        ),
        "store" to false,
    )

    private fun tolkSvar(svar: JsonNode): List<FunnetVare> {
        val status = svar.path("status").asString("")
        if (status != "completed") {
            val feil = svar.path("errors").joinToString { it.path("code").asString("") + ": " + it.path("message").asString("") }
            log.warn("Gemini svarte med status «{}» {}", status, feil)
            if (feil.contains("safety", ignoreCase = true) || feil.contains("block", ignoreCase = true)) {
                throw ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "ai_avviste",
                    "AI-en kunne ikke analysere dette bildet. Prøv et annet bilde.",
                )
            }
            throw aiFeil("status $status")
        }
        val tekst = svar.path("steps")
            .filter { it.path("type").asString("") == "model_output" }
            .flatMap { it.path("content") }
            .filter { it.path("type").asString("") == "text" }
            .joinToString("") { it.path("text").asString("") }
        if (tekst.isBlank()) throw aiFeil("svaret mangler tekst")
        return try {
            AnalysePrompt.tolk(tekst, mapper)
        } catch (e: IllegalArgumentException) {
            throw aiFeil("kunne ikke tolke svaret: ${e.message}")
        }
    }

    private fun forbruk(svar: JsonNode): Tokenforbruk {
        val brukt = svar.path("model").asString(modell)
        val bruk = svar.path("usage")
        val inn = bruk.path("total_input_tokens").asLong(0)
        // Tenkning faktureres som output.
        val ut = bruk.path("total_output_tokens").asLong(0) + bruk.path("total_thought_tokens").asLong(0)
        val kostnad = PRISER[brukt]?.let { (innPris, utPris) -> (inn * innPris + ut * utPris) / 1_000_000.0 }
        return Tokenforbruk(brukt, inn, ut, kostnad)
    }

    private fun oversett(e: RestClientResponseException): ApiException {
        val kode = e.statusCode.value()
        return when {
            kode == 429 -> {
                log.warn("Gemini rate limit: {}", e.responseBodyAsString.take(300))
                ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit", "AI-tjenesten har for mye å gjøre. Prøv igjen om litt.")
            }
            kode in setOf(500, 502, 503, 504) -> {
                log.warn("Gemini utilgjengelig ({}): {}", kode, e.responseBodyAsString.take(300))
                ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_utilgjengelig", "AI-tjenesten er utilgjengelig akkurat nå.")
            }
            kode == 401 || kode == 403 -> {
                log.error("Gemini avviste API-nøkkelen ({}). Sjekk GEMINI_API_KEY.", kode)
                aiFeil(null)
            }
            else -> {
                log.error("Feil fra Gemini ({}): {}", kode, e.responseBodyAsString.take(500))
                aiFeil(null)
            }
        }
    }

    private fun aiFeil(grunn: String?): ApiException {
        if (grunn != null) log.error("Gemini: {}", grunn)
        return ApiException(HttpStatus.BAD_GATEWAY, "ai_feil", "Noe gikk galt hos AI-tjenesten. Prøv igjen.")
    }

    companion object {
        const val STANDARD_MODELL = "gemini-4-hydrogen"
        const val STANDARD_BASE_URL = "https://generativelanguage.googleapis.com"

        /** Versjonen av Interactions-API-et koden er skrevet mot. */
        const val API_REVISJON = "2026-05-20"

        /**
         * Betalt listepris i USD per million tokens (inn, ut), per november 2026.
         * På gratisnivået koster kallene ingenting; tallet i loggen viser da hva
         * det ville kostet betalt, så leverandørene kan sammenlignes.
         */
        val PRISER: Map<String, Pair<Double, Double>> = mapOf(
            "gemini-4-hydrogen" to (1.50 to 7.50),
            "gemini-3.8-flash" to (0.75 to 3.75),
            "gemini-3.5-flash-lite" to (0.30 to 2.50),
        )
    }
}
