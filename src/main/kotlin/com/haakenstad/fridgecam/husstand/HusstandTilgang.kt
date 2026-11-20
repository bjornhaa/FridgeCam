package com.haakenstad.fridgecam.husstand

import com.haakenstad.fridgecam.common.ApiException
import com.haakenstad.fridgecam.kjoleskap.Kjoleskap
import com.haakenstad.fridgecam.kjoleskap.KjoleskapRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

/**
 * Hvilken husstand en bruker handler på vegne av, og om den har tilgang til en
 * annen husstands data. Alle API-er som leser eller skriver husstandsdata går hit.
 */
@Component
class HusstandTilgang(
    private val husstander: HusstandRepository,
    private val kjoleskap: KjoleskapRepository,
) {

    /**
     * Husstanden brukerens skanninger og beholdning hører til. Inntil man kan
     * velge (fase 6) er det den eldste husstanden brukeren er med i.
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

    /**
     * Kjøleskapet brukeren handler på (#61). Uten [kjoleskapId] brukes husstandens
     * eldste (standard). Et kjøleskap i en annen husstand gir 404, som [sjekkMedlem].
     */
    fun kjoleskapFor(brukerId: Long, kjoleskapId: Long?): Kjoleskap {
        if (kjoleskapId == null) {
            return kjoleskap.forHusstand(husstandFor(brukerId)).firstOrNull() ?: throw ikkeFunnet()
        }
        val funnet = kjoleskap.finn(kjoleskapId) ?: throw ikkeFunnet()
        sjekkMedlem(brukerId, funnet.husstandId)
        return funnet
    }

    companion object {
        fun ikkeFunnet() = ApiException(HttpStatus.NOT_FOUND, "ikke_funnet", "Fant ikke det du ba om.")
    }
}
