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

    override fun analyser(bilde: ByteArray, mimeType: String): Analyseresultat {
        if (!forsinkelse.isZero) Thread.sleep(forsinkelse)
        return Analyseresultat(VARER)
    }

    companion object {
        val VARER = listOf(
            FunnetVare("melk", 1, Sikkerhet.HOY, listOf(Boks(80, 60, 260, 420))),
            FunnetVare("egg", 6, Sikkerhet.HOY, listOf(Boks(320, 300, 620, 420))),
            FunnetVare("smør", 1, Sikkerhet.HOY, listOf(Boks(660, 330, 860, 420))),
            FunnetVare("gulost", 1, Sikkerhet.MIDDELS, listOf(Boks(100, 520, 380, 640))),
            FunnetVare(
                "yoghurt", 3, Sikkerhet.MIDDELS,
                listOf(Boks(450, 500, 560, 650), Boks(570, 500, 680, 650), Boks(690, 500, 800, 650)),
            ),
            FunnetVare("agurk", 1, Sikkerhet.LAV, listOf(Boks(150, 800, 700, 920))),
        )
    }
}
