package com.haakenstad.fridgecam.kjoleskap

import com.haakenstad.fridgecam.common.ApiException
import com.haakenstad.fridgecam.husstand.HusstandTilgang
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Legge til, gi nytt navn og slette kjøleskap (#62). Alle i husstanden kan
 * gjøre det. En husstand har alltid minst ett kjøleskap, og høyst [MAKS].
 */
@Service
class KjoleskapService(
    private val kjoleskap: KjoleskapRepository,
    private val tilgang: HusstandTilgang,
) {

    @Transactional
    fun opprett(brukerId: Long, navn: String): List<Kjoleskap> {
        val husstandId = tilgang.husstandFor(brukerId)
        kjoleskap.lasHusstand(husstandId)
        if (kjoleskap.forHusstand(husstandId).size >= MAKS) {
            throw ApiException(HttpStatus.CONFLICT, "for_mange_kjoleskap", "En husstand kan ha høyst $MAKS kjøleskap.")
        }
        unikt { kjoleskap.opprett(husstandId, navn.trim()) }
        return kjoleskap.forHusstand(husstandId)
    }

    @Transactional
    fun giNyttNavn(brukerId: Long, kjoleskapId: Long, navn: String): List<Kjoleskap> {
        val k = tilgang.kjoleskapFor(brukerId, kjoleskapId)
        unikt { kjoleskap.settNavn(k.id, navn.trim()) }
        return kjoleskap.forHusstand(k.husstandId)
    }

    /** Sletter kjøleskapet med beholdning, skanninger og bilde (via fremmednøklene). */
    @Transactional
    fun slett(brukerId: Long, kjoleskapId: Long) {
        val k = tilgang.kjoleskapFor(brukerId, kjoleskapId)
        // Låsen hindrer at to samtidige slettinger tar de to siste.
        kjoleskap.lasHusstand(k.husstandId)
        if (kjoleskap.forHusstand(k.husstandId).size <= 1) {
            throw ApiException(HttpStatus.CONFLICT, "siste_kjoleskap", "Husstanden må ha minst ett kjøleskap.")
        }
        kjoleskap.slett(k.id)
    }

    private fun unikt(blokk: () -> Unit) {
        try {
            blokk()
        } catch (e: DuplicateKeyException) {
            throw ApiException(HttpStatus.CONFLICT, "kjoleskap_finnes", "Husstanden har allerede et kjøleskap med det navnet.")
        }
    }

    companion object {
        const val MAKS = 20
    }
}
