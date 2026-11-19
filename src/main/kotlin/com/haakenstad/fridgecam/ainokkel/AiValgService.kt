package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.analyse.AiLeverandor
import com.haakenstad.fridgecam.analyse.AiModeller
import com.haakenstad.fridgecam.analyse.AiValg
import com.haakenstad.fridgecam.analyse.VisionProviderFabrikk
import com.haakenstad.fridgecam.common.ApiException
import com.haakenstad.fridgecam.husstand.HusstandRepository
import com.haakenstad.fridgecam.husstand.HusstandTilgang
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

/** Alt Profil trenger for AI-innstillingene. Nøklene vises bare med de fire siste tegnene. */
data class AiInnstillinger(
    /** False med fake-leverandøren (lokalt og i tester): da trengs ingen nøkkel. */
    val kreves: Boolean,
    val valg: ValgSvar,
    val leverandorer: List<LeverandorSvar>,
)

data class ValgSvar(
    val leverandor: String,
    val modell: String,
    /** False når husstanden bruker appens standard (`AI_PROVIDER`/`AI_MODEL`). */
    val egetValg: Boolean,
)

data class LeverandorSvar(
    /** `anthropic` eller `gemini`. */
    val id: String,
    val navn: String,
    val nokkel: NokkelInfo?,
    /** Bare lokalt: ingen nøkkel lagt inn, men nøkkelen fra .env brukes. */
    val reserve: Boolean,
    val modeller: List<ModellSvar>,
)

data class ModellSvar(val id: String, val navn: String, val beskrivelse: String, val prisPerBildeUsd: Double?)

/**
 * Husstandens valg av AI-leverandør og modell (#54). Alle i husstanden kan endre
 * det; neste skanning bruker det nye valget.
 */
@Service
class AiValgService(
    private val husstander: HusstandRepository,
    private val tilgang: HusstandTilgang,
    private val nokler: AiNokkelService,
    private val fabrikk: VisionProviderFabrikk,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Valget skanninger for husstanden skal bruke. */
    fun valgFor(husstandId: Long): AiValg = husstander.aiValg(husstandId) ?: fabrikk.standardValg

    fun innstillinger(brukerId: Long): AiInnstillinger {
        val husstandId = tilgang.husstandFor(brukerId)
        val eget = husstander.aiValg(husstandId)
        val valg = eget ?: fabrikk.standardValg
        return AiInnstillinger(
            kreves = !fabrikk.erFake,
            valg = ValgSvar(valg.leverandor.navn(), valg.modell, egetValg = eget != null),
            leverandorer = VALGBARE.map { lev ->
                val nokkel = nokler.info(husstandId, lev)
                LeverandorSvar(
                    id = lev.navn(),
                    navn = AiNokkelService.leverandornavn(lev),
                    nokkel = nokkel,
                    reserve = nokkel == null && nokler.harReserve(lev),
                    modeller = AiModeller.ALLE.filter { it.leverandor == lev }
                        .map { ModellSvar(it.id, it.navn, it.beskrivelse, it.prisPerBildeUsd) },
                )
            },
        )
    }

    fun settValg(brukerId: Long, leverandor: String, modell: String): AiInnstillinger {
        val husstandId = tilgang.husstandFor(brukerId)
        val lev = leverandor(leverandor)
        val valgt = AiModeller.finn(lev, modell)
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "ugyldig_foresporsel", "Ukjent modell.")
        husstander.settAiValg(husstandId, AiValg(lev, valgt.id))
        log.info("Husstand {} valgte {} / {} (bruker {})", husstandId, lev, valgt.id, brukerId)
        return innstillinger(brukerId)
    }

    companion object {
        val VALGBARE = listOf(AiLeverandor.ANTHROPIC, AiLeverandor.GEMINI)

        private fun AiLeverandor.navn() = name.lowercase()

        /** `anthropic`/`gemini` fra API-et; fake kan ikke velges. */
        fun leverandor(id: String): AiLeverandor =
            VALGBARE.firstOrNull { it.name.equals(id.trim(), ignoreCase = true) }
                ?: throw ApiException(HttpStatus.BAD_REQUEST, "ugyldig_foresporsel", "Ukjent AI-leverandør.")
    }
}
