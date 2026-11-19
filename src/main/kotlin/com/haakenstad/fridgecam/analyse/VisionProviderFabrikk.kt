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

/** Leverandør og modell for et AI-kall: husstandens valg, eller appens standard (#54). */
data class AiValg(val leverandor: AiLeverandor, val modell: String)

/**
 * Lager en [VisionProvider] per AI-kall, med husstandens valg og nøkkel (#42, #54).
 *
 * `AI_PROVIDER`/`AI_MODEL` gir standardvalget for husstander som ikke har valgt selv.
 * Mangler `AI_PROVIDER`, eller hører `AI_MODEL` til en annen leverandør, stopper appen
 * ved oppstart i stedet for å feile på første bilde. Med `fake` (lokalt og i tester)
 * brukes fake-leverandøren uansett hva husstanden har valgt, og ingen nøkkel trengs.
 */
@Component
class VisionProviderFabrikk(private val props: AiProperties, private val mapper: JsonMapper) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val leverandor: AiLeverandor = props.provider
        ?: throw IllegalStateException(
            "AI_PROVIDER er ikke satt. Gyldige verdier: " +
                AiLeverandor.entries.joinToString { it.name.lowercase() },
        )

    /** Fake trenger ingen nøkkel og ser bort fra husstandens valg. */
    val erFake = leverandor == AiLeverandor.FAKE

    /**
     * Valget for husstander som ikke har valgt selv. Med fake vises Anthropic som
     * standard, så skjemaet i Profil kan prøves uten ekte AI.
     */
    val standardValg: AiValg = run {
        val lev = if (erFake) AiLeverandor.ANTHROPIC else leverandor
        val modell = props.model?.trim()?.takeIf { it.isNotEmpty() && !erFake }?.also { sjekkModell(lev, it) }
            ?: standardModell(lev)
        AiValg(lev, modell)
    }

    init {
        log.info(
            "AI-leverandør: {}, standardmodell {}, effort {}",
            leverandor.name.lowercase(), if (erFake) "fake" else standardValg.modell, props.effort.name.lowercase(),
        )
    }

    /**
     * En leverandør for ett kall. Lukk den etterpå (`use`), så HTTP-klienten frigjøres.
     *
     * @param nokkel husstandens nøkkel for [valg]-ets leverandør; kreves for alle unntatt fake
     */
    fun lag(valg: AiValg, nokkel: AiNokkel?): VisionProvider {
        if (erFake) return FakeVisionProvider(props.fakeForsinkelse)
        return when (valg.leverandor) {
            AiLeverandor.ANTHROPIC -> {
                val client = AnthropicOkHttpClient.builder()
                    .apiKey(krev(nokkel).verdi)
                    // Et bilde med medium effort tar typisk 5–30 s; dette er taket per forsøk.
                    .timeout(Duration.ofSeconds(90))
                    .apply { props.anthropicBaseUrl?.takeIf { it.isNotBlank() }?.let { baseUrl(it) } }
                    .build()
                AnthropicVisionProvider(client, valg.modell, mapper, props.effort)
            }
            AiLeverandor.GEMINI -> GeminiVisionProvider(krev(nokkel).verdi, valg.modell, mapper, props.effort)
            AiLeverandor.FAKE -> FakeVisionProvider(props.fakeForsinkelse)
        }
    }

    private fun krev(nokkel: AiNokkel?): AiNokkel =
        requireNotNull(nokkel) { "AI-kall krever husstandens nøkkel" }

    companion object {
        fun standardModell(leverandor: AiLeverandor): String = when (leverandor) {
            AiLeverandor.ANTHROPIC -> AnthropicVisionProvider.STANDARD_MODELL
            AiLeverandor.GEMINI -> GeminiVisionProvider.STANDARD_MODELL
            AiLeverandor.FAKE -> "fake"
        }

        /**
         * Stopper oppstart når `AI_MODEL` hører til en annen leverandør enn
         * `AI_PROVIDER` (f.eks. en Claude-modell sendt til Gemini).
         */
        private fun sjekkModell(leverandor: AiLeverandor, modell: String) {
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
    }
}
