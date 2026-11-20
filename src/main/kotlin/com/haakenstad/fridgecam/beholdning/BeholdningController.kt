package com.haakenstad.fridgecam.beholdning

import com.haakenstad.fridgecam.analyse.Boks
import com.haakenstad.fridgecam.husstand.HusstandTilgang
import com.haakenstad.fridgecam.innlogging.InnloggetBruker
import com.haakenstad.fridgecam.skanning.SkanningRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class BeholdningSvar(
    val kjoleskap: KjoleskapInfo,
    val varer: List<BeholdningVareSvar>,
    /** Skanningen beholdningen kom fra, eller null hvis husstanden ikke har lagret noen. */
    val skanning: SkanningInfo?,
)

data class BeholdningVareSvar(val navn: String, val antall: Int, val bokser: List<Boks>)

data class SkanningInfo(val id: Long, val tidspunkt: Instant)

data class KjoleskapInfo(val id: Long, val navn: String)

@RestController
class BeholdningController(
    private val beholdning: BeholdningRepository,
    private val skanninger: SkanningRepository,
    private val tilgang: HusstandTilgang,
) {

    /** Det som er i kjøleskapet nå; uten `kjoleskap` husstandens standard kjøleskap. */
    @GetMapping("/api/beholdning")
    fun hent(
        @AuthenticationPrincipal bruker: InnloggetBruker,
        @RequestParam("kjoleskap", required = false) kjoleskapId: Long?,
    ): BeholdningSvar {
        val kjoleskap = tilgang.kjoleskapFor(bruker.brukerId, kjoleskapId)
        val varer = beholdning.forKjoleskap(kjoleskap.id)
        val skanning = varer.firstNotNullOfOrNull { it.skanningId }?.let { skanninger.finn(it) }
        return BeholdningSvar(
            kjoleskap = KjoleskapInfo(kjoleskap.id, kjoleskap.navn),
            varer = varer.map { BeholdningVareSvar(it.navn, it.antall, it.bokser) },
            skanning = skanning?.let { SkanningInfo(it.id, it.lagret ?: it.tidspunkt) },
        )
    }
}
