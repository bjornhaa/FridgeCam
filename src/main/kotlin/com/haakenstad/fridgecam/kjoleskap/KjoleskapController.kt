package com.haakenstad.fridgecam.kjoleskap

import com.haakenstad.fridgecam.husstand.HusstandTilgang
import com.haakenstad.fridgecam.innlogging.InnloggetBruker
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class KjoleskapSvar(val id: Long, val navn: String, val sistSkannet: Instant?)

@RestController
class KjoleskapController(
    private val kjoleskap: KjoleskapRepository,
    private val tilgang: HusstandTilgang,
) {

    /** Husstandens kjøleskap, eldste (standard) først. */
    @GetMapping("/api/kjoleskap")
    fun liste(@AuthenticationPrincipal bruker: InnloggetBruker): List<KjoleskapSvar> =
        kjoleskap.forHusstand(tilgang.husstandFor(bruker.brukerId))
            .map { KjoleskapSvar(it.id, it.navn, it.sistSkannet) }
}
