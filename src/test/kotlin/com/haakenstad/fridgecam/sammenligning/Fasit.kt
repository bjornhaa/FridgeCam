package com.haakenstad.fridgecam.sammenligning

import com.haakenstad.fridgecam.analyse.FunnetVare

/**
 * Hva som faktisk er i kjøleskapet på et testbilde (#11). Ligger som JSON ved
 * siden av bildet i `testbilder/`, som holdes utenfor git.
 */
data class Fasit(
    val bilde: String,
    val beskrivelse: String = "",
    val varer: List<FasitVare>,
)

data class FasitVare(
    val navn: String,
    val antall: Int = 1,
    /** Delvis skjult, uklar emballasje o.l. Telles for seg i rapporten. */
    val vanskelig: Boolean = false,
    /** Andre navn som også regnes som treff, f.eks. «ost» for gulost. */
    val alias: List<String> = emptyList(),
)

/** Resultatet av å sammenligne én AI-kjøring med fasiten. */
data class Poeng(
    val antallFasit: Int,
    val treff: List<FasitVare>,
    val mangler: List<FasitVare>,
    /** Varer AI-en fant som ikke finnes i fasiten. */
    val bomskudd: List<String>,
    /** Ekstra treff på en fasitvare som allerede var funnet (f.eks. tre «brus» listet hver for seg). */
    val duplikater: List<String>,
) {
    /** Andel av det AI-en fant som var riktig. */
    val presisjon: Double get() = if (treff.isEmpty() && bomskudd.isEmpty()) 1.0 else treff.size.toDouble() / (treff.size + bomskudd.size)

    /** Andel av fasiten AI-en fant. */
    val dekning: Double get() = treff.size.toDouble() / antallFasit

    fun dekning(vanskelig: Boolean): Double {
        val totalt = (treff + mangler).count { it.vanskelig == vanskelig }
        return if (totalt == 0) 1.0 else treff.count { it.vanskelig == vanskelig }.toDouble() / totalt
    }
}

object Poengsetter {

    /**
     * Kobler hver vare AI-en fant mot fasiten. Et navn kan passe flere fasitvarer
     * (f.eks. «smør» passer både smør, Brelett og Olivero); da velges en som ikke
     * er funnet ennå, og treff på hovednavn kobles før treff via alias.
     */
    fun vurder(fasit: Fasit, funnet: List<FunnetVare>): Poeng {
        val gjenstaende = fasit.varer.toMutableList()
        val treff = mutableListOf<FasitVare>()
        val ukoblet = mutableListOf<FunnetVare>()

        // Runde 1: navn som er en fasitvares hovednavn kobles først. Ellers kunne et
        // generelt navn som «ost» tatt gulosten før «gulost» kom fram i listen.
        for (vare in funnet) {
            val hoved = gjenstaende.firstOrNull { normaliser(it.navn) == normaliser(vare.navn) }
            if (hoved != null) {
                gjenstaende -= hoved
                treff += hoved
            } else {
                ukoblet += vare
            }
        }

        // Runde 2: resten kobles via alias, til en fasitvare som ikke er funnet ennå.
        val bomskudd = mutableListOf<String>()
        val duplikater = mutableListOf<String>()
        for (vare in ukoblet) {
            val navn = normaliser(vare.navn)
            val passer = fasit.varer.filter { navn == normaliser(it.navn) || it.alias.any { a -> normaliser(a) == navn } }
            val ledig = passer.firstOrNull { it in gjenstaende }
            when {
                passer.isEmpty() -> bomskudd += vare.navn
                ledig != null -> {
                    gjenstaende -= ledig
                    treff += ledig
                }
                else -> duplikater += vare.navn
            }
        }
        return Poeng(fasit.varer.size, treff, gjenstaende, bomskudd, duplikater)
    }

    /** «Coca-Cola» og «coca cola» skal regnes som samme navn. */
    fun normaliser(navn: String): String = navn.lowercase().filter { it.isLetterOrDigit() }
}
