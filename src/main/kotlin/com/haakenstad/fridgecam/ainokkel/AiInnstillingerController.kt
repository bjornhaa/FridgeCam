package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.innlogging.InnloggetBruker
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Ikke data class: [toString] skal ikke kunne vise nøkkelen. */
class AiNokkelForesporsel(val leverandor: String, val nokkel: String) {
    override fun toString() = "AiNokkelForesporsel(leverandor=$leverandor, nokkel=skjult)"
}

data class AiValgForesporsel(val leverandor: String, val modell: String)

/**
 * Husstandens AI-innstillinger: valg av leverandør og modell (#54) og nøkkel per
 * leverandør (#42). Nøklene kan bare skrives, aldri leses ut igjen.
 */
@RestController
@RequestMapping("/api/husstand/ai")
class AiInnstillingerController(
    private val valg: AiValgService,
    private val nokler: AiNokkelService,
) {

    @GetMapping
    fun hent(@AuthenticationPrincipal bruker: InnloggetBruker): AiInnstillinger =
        valg.innstillinger(bruker.brukerId)

    @PutMapping("/valg")
    fun velg(
        @AuthenticationPrincipal bruker: InnloggetBruker,
        @RequestBody foresporsel: AiValgForesporsel,
    ): AiInnstillinger = valg.settValg(bruker.brukerId, foresporsel.leverandor, foresporsel.modell)

    @PutMapping("/nokkel")
    fun lagreNokkel(
        @AuthenticationPrincipal bruker: InnloggetBruker,
        @RequestBody foresporsel: AiNokkelForesporsel,
    ): AiInnstillinger {
        nokler.lagre(bruker.brukerId, AiValgService.leverandor(foresporsel.leverandor), foresporsel.nokkel)
        return valg.innstillinger(bruker.brukerId)
    }

    @DeleteMapping("/nokkel/{leverandor}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun fjernNokkel(@AuthenticationPrincipal bruker: InnloggetBruker, @PathVariable leverandor: String) =
        nokler.fjern(bruker.brukerId, AiValgService.leverandor(leverandor))
}
