package com.haakenstad.fridgecam.analyse

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.OverloadedException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.ServiceUnavailableException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaFallbacksParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.haakenstad.fridgecam.common.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper
import java.util.Base64

/**
 * Claude via den offisielle Anthropic Java SDK-en (#9).
 *
 * Bruker beta-endepunktet fordi server-side fallbacks er beta: hvis modellens
 * sikkerhetsfiltre avviser et bilde, prøver API-et selv en annen modell i
 * stedet for at brukeren får feil.
 */
class AnthropicVisionProvider(
    private val client: AnthropicClient,
    private val modell: String,
    private val mapper: JsonMapper,
    private val innsats: Innsats = Innsats.MEDIUM,
) : VisionProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val navn = "anthropic/$modell (effort ${innsats.name.lowercase()})"

    private fun skjema(format: Boksformat) = BetaJsonOutputFormat.Schema.builder()
        .putAllAdditionalProperties(AnalysePrompt.skjema(format).mapValues { JsonValue.from(it.value) })
        .build()

    override fun analyser(bilde: ByteArray, mimeType: String): Analyseresultat {
        // Claude oppgir bokser i piksler (#58). Kjenner vi bildets størrelse, ber vi om
        // piksler og regner om selv; ellers ber vi om 0–1000 som før.
        val storrelse = Bildeformat.storrelse(bilde)
        val svar = try {
            client.beta().messages().create(lagForesporsel(bilde, mimeType, storrelse))
        } catch (e: AnthropicServiceException) {
            throw oversett(e)
        } catch (e: AnthropicIoException) {
            log.warn("Fikk ikke kontakt med Anthropic", e)
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_utilgjengelig", "Fikk ikke kontakt med AI-tjenesten.")
        }
        return Analyseresultat(tolkSvar(svar, storrelse), forbruk(svar))
    }

    private fun forbruk(svar: BetaMessage): Tokenforbruk {
        val brukt = svar.model().asString()
        val inn = svar.usage().inputTokens()
        val ut = svar.usage().outputTokens()
        val kostnad = PRISER[brukt]?.let { (innPris, utPris) -> (inn * innPris + ut * utPris) / 1_000_000.0 }
        return Tokenforbruk(brukt, inn, ut, kostnad)
    }

    /** Frigjør HTTP-klienten; kalles etter hvert kall (se [VisionProviderFabrikk]). */
    override fun close() = client.close()

    private fun lagForesporsel(bilde: ByteArray, mimeType: String, storrelse: Bildestorrelse?): MessageCreateParams {
        val format = if (storrelse != null) Boksformat.PIKSLER else Boksformat.HJORNER
        val builder = MessageCreateParams.builder()
            .model(modell)
            .maxTokens(16_000L)
            .system(AnalysePrompt.system(format))
            .addUserMessageOfContentBlockParams(
                listOf(
                    BetaContentBlockParam.ofImage(
                        BetaBase64ImageSource.builder()
                            .mediaType(mediaType(mimeType))
                            .data(Base64.getEncoder().encodeToString(bilde))
                            .build(),
                    ),
                    BetaContentBlockParam.ofText(AnalysePrompt.bruker(storrelse)),
                ),
            )
            .outputConfig(
                BetaOutputConfig.builder()
                    .effort(
                        when (innsats) {
                            Innsats.LOW -> BetaOutputConfig.Effort.LOW
                            Innsats.MEDIUM -> BetaOutputConfig.Effort.MEDIUM
                            Innsats.HIGH -> BetaOutputConfig.Effort.HIGH
                        },
                    )
                    .format(BetaJsonOutputFormat.builder().schema(skjema(format)).build())
                    .build(),
            )
        if (modell in MODELLER_MED_FALLBACK) {
            builder
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacks(BetaFallbacksParam.ofDefault())
        }
        return builder.build()
    }

    private fun tolkSvar(svar: BetaMessage, storrelse: Bildestorrelse?): List<FunnetVare> {
        when (svar.stopReason().orElse(null)) {
            BetaStopReason.REFUSAL -> {
                log.warn("Anthropic avviste bildet: {}", svar.stopDetails().orElse(null))
                throw ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "ai_avviste",
                    "AI-en kunne ikke analysere dette bildet. Prøv et annet bilde.",
                )
            }
            BetaStopReason.MAX_TOKENS -> {
                log.error("Anthropic-svaret ble kuttet (max_tokens)")
                throw aiFeil()
            }
            else -> Unit
        }
        val tekst = svar.content().firstNotNullOfOrNull { it.text().orElse(null)?.text() }
            ?: run {
                log.error("Anthropic-svaret mangler tekst (stop_reason={})", svar.stopReason().orElse(null))
                throw aiFeil()
            }
        return try {
            AnalysePrompt.tolk(tekst, mapper, storrelse)
        } catch (e: IllegalArgumentException) {
            log.error("Kunne ikke tolke Anthropic-svaret: {}", e.message)
            throw aiFeil()
        }
    }

    /** Gjør SDK-feil om til feilkoder appen forstår, mest spesifikke først. */
    private fun oversett(e: AnthropicServiceException): ApiException = when (e) {
        is RateLimitException -> {
            log.warn("Anthropic rate limit: {}", e.message)
            ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit", "AI-tjenesten har for mye å gjøre. Prøv igjen om litt.")
        }
        is OverloadedException, is ServiceUnavailableException, is InternalServerException -> {
            log.warn("Anthropic utilgjengelig ({}): {}", e.statusCode(), e.message)
            ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_utilgjengelig", "AI-tjenesten er utilgjengelig akkurat nå.")
        }
        is UnauthorizedException, is PermissionDeniedException -> {
            log.warn("Anthropic avviste husstandens API-nøkkel ({})", e.statusCode())
            AiNokkelFeil.ugyldig()
        }
        is BadRequestException if e.message.orEmpty().contains("credit balance", ignoreCase = true) -> {
            log.warn("Anthropic-kontoen til husstanden er tom for kreditt")
            AiNokkelFeil.ingenKreditt()
        }
        else -> {
            log.error("Feil fra Anthropic ({}): {}", e.statusCode(), e.message)
            aiFeil()
        }
    }

    private fun aiFeil() =
        ApiException(HttpStatus.BAD_GATEWAY, "ai_feil", "Noe gikk galt hos AI-tjenesten. Prøv igjen.")

    private fun mediaType(mimeType: String) = when (mimeType) {
        "image/jpeg" -> BetaBase64ImageSource.MediaType.IMAGE_JPEG
        "image/png" -> BetaBase64ImageSource.MediaType.IMAGE_PNG
        "image/webp" -> BetaBase64ImageSource.MediaType.IMAGE_WEBP
        else -> throw IllegalArgumentException("Bildeformat støttes ikke: $mimeType")
    }

    companion object {
        const val STANDARD_MODELL = "claude-opus-5-5"

        /**
         * Listepris i USD per million tokens (inn, ut), per november 2026.
         * Bare til anslag i loggen; fasit er Cost-siden i Claude Console.
         * Ut-tokens inkluderer tenkning, som faktureres som output.
         */
        val PRISER: Map<String, Pair<Double, Double>> = mapOf(
            "claude-opus-5-5" to (4.0 to 20.0),
            "claude-opus-5" to (5.0 to 25.0),
            "claude-opus-4-8" to (5.0 to 25.0),
            "claude-sonnet-5-5" to (2.0 to 10.0),
            "claude-sonnet-5" to (2.0 to 10.0),
            "claude-haiku-5-5" to (0.10 to 0.50),
            "claude-fable-5-2" to (8.0 to 40.0),
        )

        /** Modeller som støtter server-side fallbacks med `fallbacks: "default"`. */
        val MODELLER_MED_FALLBACK = setOf(
            "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5", "claude-fable-5-1", "claude-fable-5-2",
        )
    }
}
