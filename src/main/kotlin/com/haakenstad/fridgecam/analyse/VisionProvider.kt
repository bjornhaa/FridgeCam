package com.haakenstad.fridgecam.analyse

import com.fasterxml.jackson.annotation.JsonValue

/**
 * En AI-leverandør som kan se på et bilde av kjøleskapet og liste varene (#8).
 *
 * Resten av appen kjenner bare dette grensesnittet, så leverandøren kan byttes
 * med `AI_PROVIDER` uten andre endringer. Lages per kall med husstandens nøkkel.
 */
interface VisionProvider : AutoCloseable {

    /** Vises i svaret og i logger, f.eks. «anthropic/claude-opus-5-5». */
    val navn: String

    /**
     * @param bilde bildedata, allerede validert som et støttet format
     * @param mimeType `image/jpeg`, `image/png` eller `image/webp`
     */
    fun analyser(bilde: ByteArray, mimeType: String): Analyseresultat

    /** Leverandøren lages per kall ([VisionProviderFabrikk]) og lukkes etterpå. */
    override fun close() {}
}

data class Analyseresultat(
    val varer: List<FunnetVare>,
    /** Tokenforbruk for kallet, når leverandøren oppgir det. */
    val forbruk: Tokenforbruk? = null,
)

/**
 * @param modell modellen som faktisk svarte (kan avvike fra den konfigurerte ved fallback)
 * @param kostnadUsd anslått ut fra listepris; null når modellen ikke har kjent pris
 */
data class Tokenforbruk(
    val modell: String,
    val inn: Long,
    val ut: Long,
    val kostnadUsd: Double?,
)

data class FunnetVare(
    val navn: String,
    val antall: Int,
    val sikkerhet: Sikkerhet,
    /** Hvor i bildet varen er; én boks per synlig eksemplar (eller gruppe). Kan være tom. */
    val bokser: List<Boks> = emptyList(),
)

/**
 * Et rektangel i bildet, i normaliserte koordinater 0–[MAKS] relativt til
 * bildets bredde og høyde, med origo øverst til venstre. (x1, y1) er øvre
 * venstre hjørne, (x2, y2) nedre høyre. Uavhengig av hvor stort bildet vises.
 */
data class Boks(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
    companion object {
        const val MAKS = 1000
    }
}

/** Hvor sikker AI-en er på at varen faktisk er der. */
enum class Sikkerhet(@get:JsonValue val verdi: String) {
    HOY("høy"),
    MIDDELS("middels"),
    LAV("lav"),
}
