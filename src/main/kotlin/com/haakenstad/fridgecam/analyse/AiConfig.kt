package com.haakenstad.fridgecam.analyse

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

enum class AiLeverandor { FAKE, ANTHROPIC, GEMINI }

/**
 * Hvor mye modellen skal tenke før den svarer (`AI_EFFORT`). Mer tenking kan gi
 * bedre svar, men tar lengre tid og koster mer, siden tenking faktureres som output.
 * Blir `effort` hos Anthropic og `thinking_level` hos Gemini.
 */
enum class Innsats { LOW, MEDIUM, HIGH }

/**
 * `fridgecam.ai.*`, satt fra miljøvariablene `AI_PROVIDER` og `AI_MODEL`.
 */
@ConfigurationProperties("fridgecam.ai")
data class AiProperties(
    val provider: AiLeverandor? = null,
    val model: String? = null,
    val fakeForsinkelse: Duration = Duration.ZERO,
    val anthropicApiKey: String? = null,
    val geminiApiKey: String? = null,
    val effort: Innsats = Innsats.MEDIUM,
)

@Configuration
class AiConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Velger leverandør ved oppstart. Mangler noe, stopper appen med en
     * tydelig melding i stedet for å feile på første bilde.
     */
    @Bean
    fun visionProvider(props: AiProperties, mapper: JsonMapper): VisionProvider {
        val leverandor = props.provider
            ?: throw IllegalStateException(
                "AI_PROVIDER er ikke satt. Gyldige verdier: " +
                    AiLeverandor.entries.joinToString { it.name.lowercase() },
            )
        val provider = when (leverandor) {
            AiLeverandor.FAKE -> FakeVisionProvider(props.fakeForsinkelse)
            AiLeverandor.ANTHROPIC -> anthropic(props, mapper)
            AiLeverandor.GEMINI -> gemini(props, mapper)
        }
        log.info("AI-leverandør: {}", provider.navn)
        return provider
    }

    private fun anthropic(props: AiProperties, mapper: JsonMapper): AnthropicVisionProvider {
        val nokkel = props.anthropicApiKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("AI_PROVIDER=anthropic krever ANTHROPIC_API_KEY.")
        val client = AnthropicOkHttpClient.builder()
            .apiKey(nokkel)
            // Et bilde med medium effort tar typisk 5–30 s; dette er taket per forsøk.
            .timeout(Duration.ofSeconds(90))
            .build()
        val modell = props.model?.takeIf { it.isNotBlank() } ?: AnthropicVisionProvider.STANDARD_MODELL
        return AnthropicVisionProvider(client, modell, mapper, props.effort)
    }

    private fun gemini(props: AiProperties, mapper: JsonMapper): GeminiVisionProvider {
        val nokkel = props.geminiApiKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("AI_PROVIDER=gemini krever GEMINI_API_KEY.")
        val modell = props.model?.takeIf { it.isNotBlank() } ?: GeminiVisionProvider.STANDARD_MODELL
        return GeminiVisionProvider(nokkel, modell, mapper, props.effort)
    }
}
