package com.haakenstad.fridgecam.analyse

/** En modell husstanden kan velge (#54). [id] er det leverandørens API kaller den. */
data class AiModell(
    val leverandor: AiLeverandor,
    val id: String,
    val navn: String,
    val beskrivelse: String,
) {
    /**
     * Omtrentlig pris per bilde i USD ut fra listepris og et typisk kall
     * (≈ 4000 tokens inn, 2000 ut med medium effort). Null når prisen er ukjent.
     */
    val prisPerBildeUsd: Double? = when (leverandor) {
        AiLeverandor.ANTHROPIC -> AnthropicVisionProvider.PRISER[id]
        AiLeverandor.GEMINI -> GeminiVisionProvider.PRISER[id]
        AiLeverandor.FAKE -> null
    }?.let { (inn, ut) -> (TYPISK_INN * inn + TYPISK_UT * ut) / 1_000_000.0 }

    private companion object {
        const val TYPISK_INN = 4_000
        const val TYPISK_UT = 2_000
    }
}

/**
 * Modellene husstanden kan velge mellom. Bare disse godtas, så en feilskrevet
 * modell ikke gir feil på hver skanning. Rekkefølgen er visningsrekkefølgen.
 */
object AiModeller {
    val ALLE = listOf(
        AiModell(AiLeverandor.ANTHROPIC, "claude-opus-5-5", "Claude Opus 5.5", "Finner flest varer. Anbefalt."),
        AiModell(AiLeverandor.ANTHROPIC, "claude-sonnet-5-5", "Claude Sonnet 5.5", "Nesten like god, halv pris."),
        AiModell(AiLeverandor.ANTHROPIC, "claude-haiku-5-5", "Claude Haiku 5.5", "Billigst, men finner færre varer."),
        AiModell(AiLeverandor.GEMINI, "gemini-4-hydrogen", "Gemini 4 Hydrogen", "Googles beste. Gratisnivået avviser ofte kall."),
        AiModell(AiLeverandor.GEMINI, "gemini-3.8-flash", "Gemini 3.8 Flash", "Raskere og billigere."),
        AiModell(AiLeverandor.GEMINI, "gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Billigst hos Google."),
    )

    fun finn(leverandor: AiLeverandor, id: String): AiModell? =
        ALLE.firstOrNull { it.leverandor == leverandor && it.id == id }
}
