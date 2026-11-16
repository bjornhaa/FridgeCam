package com.haakenstad.fridgecam.analyse

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

enum class AiLeverandor { FAKE, ANTHROPIC, GEMINI }

/**
 * `fridgecam.ai.*`, satt fra miljøvariablene `AI_PROVIDER` og `AI_MODEL`.
 */
@ConfigurationProperties("fridgecam.ai")
data class AiProperties(
    val provider: AiLeverandor? = null,
    val model: String? = null,
    val fakeForsinkelse: Duration = Duration.ZERO,
)

@Configuration
class AiConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Velger leverandør ved oppstart. Mangler noe, stopper appen med en
     * tydelig melding i stedet for å feile på første bilde.
     */
    @Bean
    fun visionProvider(props: AiProperties): VisionProvider {
        val leverandor = props.provider
            ?: throw IllegalStateException(
                "AI_PROVIDER er ikke satt. Gyldige verdier: " +
                    AiLeverandor.entries.joinToString { it.name.lowercase() },
            )
        val provider = when (leverandor) {
            AiLeverandor.FAKE -> FakeVisionProvider(props.fakeForsinkelse)
            AiLeverandor.ANTHROPIC -> ikkeImplementert(leverandor, "#9")
            AiLeverandor.GEMINI -> ikkeImplementert(leverandor, "#10")
        }
        log.info("AI-leverandør: {}", provider.navn)
        return provider
    }

    private fun ikkeImplementert(leverandor: AiLeverandor, issue: String): Nothing =
        throw IllegalStateException(
            "AI_PROVIDER=${leverandor.name.lowercase()} er ikke implementert ennå ($issue). Bruk fake inntil videre.",
        )
}
