package com.haakenstad.fridgecam.innlogging

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class InnloggingController(private val innlogging: InnloggingService) {

    @PostMapping("/auth/registrer")
    fun registrer(@Valid @RequestBody foresporsel: RegistrerForesporsel): InnloggingSvar =
        innlogging.registrer(foresporsel)

    @PostMapping("/auth/logg-inn")
    fun loggInn(@Valid @RequestBody foresporsel: LoggInnForesporsel): InnloggingSvar =
        innlogging.loggInn(foresporsel)

    @GetMapping("/meg")
    fun meg(@AuthenticationPrincipal bruker: InnloggetBruker): MegSvar =
        innlogging.meg(bruker.brukerId)
}
