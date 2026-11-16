package com.haakenstad.fridgecam.analyse

import com.fasterxml.jackson.annotation.JsonValue

/**
 * En AI-leverandør som kan se på et bilde av kjøleskapet og liste varene (#8).
 *
 * Resten av appen kjenner bare dette grensesnittet, så leverandøren kan byttes
 * med `AI_PROVIDER` uten andre endringer.
 */
interface VisionProvider {

    /** Vises i svaret og i logger, f.eks. «anthropic/claude-opus-5-5». */
    val navn: String

    /**
     * @param bilde bildedata, allerede validert som et støttet format
     * @param mimeType `image/jpeg`, `image/png` eller `image/webp`
     */
    fun analyser(bilde: ByteArray, mimeType: String): List<FunnetVare>
}

data class FunnetVare(
    val navn: String,
    val antall: Int,
    val sikkerhet: Sikkerhet,
)

/** Hvor sikker AI-en er på at varen faktisk er der. */
enum class Sikkerhet(@get:JsonValue val verdi: String) {
    HOY("høy"),
    MIDDELS("middels"),
    LAV("lav"),
}
