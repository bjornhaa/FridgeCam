package com.haakenstad.fridgecam.husstand

import com.haakenstad.fridgecam.common.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

/**
 * Hvilken husstand en bruker handler på vegne av, og om den har tilgang til en
 * annen husstands data. Alle API-er som leser eller skriver husstandsdata går hit.
 */
@Component
class HusstandTilgang(private val husstander: HusstandRepository) {

    /**
     * Husstanden brukerens skanninger og beholdning hører til. Inntil man kan
     * velge (fase 5) er det den eldste husstanden brukeren er med i.
     */
    fun husstandFor(brukerId: Long): Long =
        husstander.forBruker(brukerId).firstOrNull()?.husstandId
            ?: throw ApiException(HttpStatus.FORBIDDEN, "ingen_husstand", "Du er ikke med i noen husstand.")

    /**
     * Kaster 404 (ikke 403) når brukeren ikke er medlem, så man ikke kan
     * finne ut hvilke id-er som finnes hos andre.
     */
    fun sjekkMedlem(brukerId: Long, husstandId: Long) {
        if (husstander.forBruker(brukerId).none { it.husstandId == husstandId }) {
            throw ikkeFunnet()
        }
    }

    companion object {
        fun ikkeFunnet() = ApiException(HttpStatus.NOT_FOUND, "ikke_funnet", "Fant ikke det du ba om.")
    }
}
