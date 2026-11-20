package com.haakenstad.fridgecam.kjoleskap

import com.haakenstad.fridgecam.husstand.HusstandTilgang
import com.haakenstad.fridgecam.innlogging.InnloggetBruker
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class KjoleskapSvar(val id: Long, val navn: String, val sistSkannet: Instant?)

data class KjoleskapForesporsel(
    @field:NotBlank(message = "Kjøleskapet må ha et navn.")
    @field:Size(max = 40, message = "Navnet kan ha høyst 40 tegn.")
    val navn: String = "",
)

/** Husstandens kjøleskap (#61, #62). Endringer svarer med hele listen, eldste (standard) først. */
@RestController
@RequestMapping("/api/kjoleskap")
class KjoleskapController(
    private val kjoleskap: KjoleskapRepository,
    private val service: KjoleskapService,
    private val tilgang: HusstandTilgang,
) {

    @GetMapping
    fun liste(@AuthenticationPrincipal bruker: InnloggetBruker): List<KjoleskapSvar> =
        kjoleskap.forHusstand(tilgang.husstandFor(bruker.brukerId)).svar()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun opprett(
        @AuthenticationPrincipal bruker: InnloggetBruker,
        @Valid @RequestBody foresporsel: KjoleskapForesporsel,
    ): List<KjoleskapSvar> = service.opprett(bruker.brukerId, foresporsel.navn).svar()

    @PutMapping("/{id}")
    fun giNyttNavn(
        @AuthenticationPrincipal bruker: InnloggetBruker,
        @PathVariable id: Long,
        @Valid @RequestBody foresporsel: KjoleskapForesporsel,
    ): List<KjoleskapSvar> = service.giNyttNavn(bruker.brukerId, id, foresporsel.navn).svar()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun slett(@AuthenticationPrincipal bruker: InnloggetBruker, @PathVariable id: Long) =
        service.slett(bruker.brukerId, id)

    private fun List<Kjoleskap>.svar() = map { KjoleskapSvar(it.id, it.navn, it.sistSkannet) }
}
