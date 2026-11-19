package com.haakenstad.fridgecam.analyse

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.haakenstad.fridgecam.ainokkel.AiNokkel
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
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
 * `fridgecam.ai.*`, satt fra miljøvariablene `AI_PROVIDER`, `AI_MODEL` og `AI_EFFORT`.
 *
 * [anthropicApiKey] og [geminiApiKey] er bare lokal reserve (application-local.yml);
 * i prod bruker hver husstand sin egen nøkkel (#42).
 */
@ConfigurationProperties("fridgecam.ai")
data class AiProperties(
    val provider: AiLeverandor? = null,
    val model: String? = null,
    val fakeForsinkelse: Duration = Duration.ZERO,
    val anthropicApiKey: String? = null,
    val geminiApiKey: String? = null,
    val effort: Innsats = Innsats.MEDIUM,
    /** Bare for tester (falsk Anthropic-server). */
    val anthropicBaseUrl: String? = null,
) {
    // Data class-ens toString ville vist nøklene.
    override fun toString() = "AiProperties(provider=$provider, model=$model, effort=$effort)"
}

/**
 * Lager en [VisionProvider] per AI-kall, med husstandens nøkkel (#42).
 *
 * Leverandør og modell velges ved oppstart (`AI_PROVIDER`, `AI_MODEL`); mangler
 * `AI_PROVIDER`, stopper appen med en tydelig melding i stedet for å feile på
 * første bilde.
 */
@Component
class VisionProviderFabrikk(private val props: AiProperties, private val mapper: JsonMapper) {

    private val log = LoggerFactory.getLogger(javaClass)

    val leverandor: AiLeverandor = props.provider
        ?: throw IllegalStateException(
            "AI_PROVIDER er ikke satt. Gyldige verdier: " +
                AiLeverandor.entries.joinToString { it.name.lowercase() },
        )

    /** Fake trenger ingen nøkkel. */
    val kreverNokkel = leverandor != AiLeverandor.FAKE

    /**
     * Leverandøren husstandens nøkkel gjelder for. Med fake lagres den som en
     * Anthropic-nøkkel, så skjemaet i Profil kan prøves uten ekte AI.
     */
    val nokkelLeverandor = if (leverandor == AiLeverandor.FAKE) AiLeverandor.ANTHROPIC else leverandor

    private val modell: String = props.model?.trim()?.takeIf { it.isNotEmpty() }?.also(::sjekkModell) ?: when (leverandor) {
        AiLeverandor.ANTHROPIC -> AnthropicVisionProvider.STANDARD_MODELL
        AiLeverandor.GEMINI -> GeminiVisionProvider.STANDARD_MODELL
        AiLeverandor.FAKE -> "fake"
    }

    /**
     * Stopper oppstart når `AI_MODEL` hører til en annen leverandør enn
     * `AI_PROVIDER` (f.eks. en Claude-modell sendt til Gemini), i stedet for at
     * hver skanning feiler. Fake bryr seg ikke om modellen.
     */
    private fun sjekkModell(modell: String) {
        val (prefiks, navn) = when (leverandor) {
            AiLeverandor.ANTHROPIC -> "claude-" to "Anthropic"
            AiLeverandor.GEMINI -> "gemini-" to "Gemini"
            AiLeverandor.FAKE -> return
        }
        check(modell.startsWith(prefiks)) {
            "AI_MODEL=$modell passer ikke med AI_PROVIDER=${leverandor.name.lowercase()}: " +
                "$navn-modeller begynner med «$prefiks». Rett AI_MODEL, eller fjern den for å bruke standardmodellen."
        }
    }

    init {
        log.info("AI-leverandør: {}, modell {}, effort {}", leverandor.name.lowercase(), modell, props.effort.name.lowercase())
    }

    /**
     * En leverandør for ett kall. Lukk den etterpå (`use`), så HTTP-klienten frigjøres.
     *
     * @param nokkel husstandens nøkkel; kreves for alle unntatt fake
     */
    fun lag(nokkel: AiNokkel?): VisionProvider = when (leverandor) {
        AiLeverandor.FAKE -> FakeVisionProvider(props.fakeForsinkelse)
        AiLeverandor.ANTHROPIC -> {
            val client = AnthropicOkHttpClient.builder()
                .apiKey(krev(nokkel).verdi)
                // Et bilde med medium effort tar typisk 5–30 s; dette er taket per forsøk.
                .timeout(Duration.ofSeconds(90))
                .apply { props.anthropicBaseUrl?.takeIf { it.isNotBlank() }?.let { baseUrl(it) } }
                .build()
            AnthropicVisionProvider(client, modell, mapper, props.effort)
        }
        AiLeverandor.GEMINI -> GeminiVisionProvider(krev(nokkel).verdi, modell, mapper, props.effort)
    }

    private fun krev(nokkel: AiNokkel?): AiNokkel =
        requireNotNull(nokkel) { "AI_PROVIDER=${leverandor.name.lowercase()} krever husstandens nøkkel" }
}
