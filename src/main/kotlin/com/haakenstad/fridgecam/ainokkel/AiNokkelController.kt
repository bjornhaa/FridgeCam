package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.innlogging.InnloggetBruker
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Ikke data class: [toString] skal ikke kunne vise nøkkelen. */
class AiNokkelForesporsel(val nokkel: String) {
    override fun toString() = "AiNokkelForesporsel(nokkel=skjult)"
}

/**
 * Husstandens AI-nøkkel (#42). Nøkkelen kan bare skrives, aldri leses ut igjen:
 * svarene har bare de fire siste tegnene.
 */
@RestController
@RequestMapping("/api/husstand/ai-nokkel")
class AiNokkelController(private val nokler: AiNokkelService) {

    @GetMapping
    fun hent(@AuthenticationPrincipal bruker: InnloggetBruker): AiNokkelStatus =
        nokler.status(bruker.brukerId)

    @PutMapping
    fun lagre(
        @AuthenticationPrincipal bruker: InnloggetBruker,
        @RequestBody foresporsel: AiNokkelForesporsel,
    ): AiNokkelStatus = nokler.lagre(bruker.brukerId, foresporsel.nokkel)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun fjern(@AuthenticationPrincipal bruker: InnloggetBruker) = nokler.fjern(bruker.brukerId)
}
