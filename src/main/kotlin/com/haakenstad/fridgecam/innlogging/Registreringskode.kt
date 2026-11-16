package com.haakenstad.fridgecam.innlogging

import com.haakenstad.fridgecam.common.ApiException
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.security.MessageDigest

/** `fridgecam.registrering.kode`, fra `FRIDGECAM_REGISTRERINGSKODE`. */
@ConfigurationProperties("fridgecam.registrering")
data class RegistreringEgenskaper(
    val kode: String? = null,
)

/**
 * Registrering krever en kode (#27), så ikke hvem som helst som finner appen
 * kan bruke AI-kreditten. Uten satt kode er registrering stengt.
 */
@Component
class Registreringskode(egenskaper: RegistreringEgenskaper) {

    private val kode = egenskaper.kode?.trim()?.takeIf { it.isNotEmpty() }

    fun sjekk(oppgitt: String) {
        if (kode == null) {
            throw ApiException(HttpStatus.FORBIDDEN, "registrering_stengt", "Registrering er stengt.")
        }
        // Sammenligning på konstant tid, så svartiden ikke røper hvor mye av koden som stemmer.
        val riktig = MessageDigest.isEqual(
            oppgitt.trim().toByteArray(Charsets.UTF_8),
            kode.toByteArray(Charsets.UTF_8),
        )
        if (!riktig) {
            throw ApiException(HttpStatus.FORBIDDEN, "ugyldig_registreringskode", "Feil registreringskode.")
        }
    }
}
