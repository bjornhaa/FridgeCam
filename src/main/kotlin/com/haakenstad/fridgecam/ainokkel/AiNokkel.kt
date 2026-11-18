package com.haakenstad.fridgecam.ainokkel

/**
 * En AI-nøkkel i klartekst, bare i minnet og bare så lenge et AI-kall pågår.
 *
 * Egen klasse i stedet for `String` så [toString] kan skjule verdien: havner
 * den i en loggmelding, et unntak eller en debugger-visning, står det «skjult».
 */
class AiNokkel(val verdi: String) {
    override fun toString() = "AiNokkel(skjult)"
}
