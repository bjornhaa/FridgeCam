package com.haakenstad.fridgecam.analyse

import java.time.Duration

/**
 * Leverandør uten AI, for lokal utvikling og tester. Svarer alltid med samme
 * liste, slik at hele flyten kan prøves uten API-nøkkel og uten kostnad.
 *
 * [forsinkelse] etterligner at et ekte AI-kall tar litt tid, så lastevisningen
 * i appen kan sjekkes.
 */
class FakeVisionProvider(
    private val forsinkelse: Duration = Duration.ZERO,
) : VisionProvider {

    override val navn = "fake"

    override fun analyser(bilde: ByteArray, mimeType: String): List<FunnetVare> {
        if (!forsinkelse.isZero) Thread.sleep(forsinkelse)
        return VARER
    }

    companion object {
        val VARER = listOf(
            FunnetVare("melk", 1, Sikkerhet.HOY),
            FunnetVare("egg", 6, Sikkerhet.HOY),
            FunnetVare("smør", 1, Sikkerhet.HOY),
            FunnetVare("gulost", 1, Sikkerhet.MIDDELS),
            FunnetVare("yoghurt", 3, Sikkerhet.MIDDELS),
            FunnetVare("agurk", 1, Sikkerhet.LAV),
        )
    }
}
