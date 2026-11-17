package com.haakenstad.fridgecam.skanning

import com.haakenstad.fridgecam.innlogging.InnloggetBruker
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import java.time.Duration
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class LagreForesporsel(
    @field:Size(max = 200, message = "For mange varer.")
    @field:Valid
    val varer: List<RettetVareForesporsel> = emptyList(),
)

data class RettetVareForesporsel(
    @field:NotBlank(message = "En vare mangler navn.")
    @field:Size(max = 100, message = "Et varenavn er for langt.")
    val navn: String = "",
    @field:Min(1, message = "Antall må være minst 1.")
    @field:Max(99, message = "Antall kan være høyst 99.")
    val antall: Int = 1,
    /** Plassen i AI-ens liste, så boksene kan hentes derfra; null for varer brukeren la til. */
    @field:PositiveOrZero
    val aiIndeks: Int? = null,
)

@RestController
@RequestMapping("/api/skanninger")
class SkanningController(private val skanninger: SkanningService) {

    /**
     * Bildet AI-en fikk, så boksene passer. Et skanningsbilde endres aldri, så
     * nettleseren kan gjerne beholde det; `private` fordi det krever innlogging.
     */
    @GetMapping("/{id}/bilde")
    fun bilde(@AuthenticationPrincipal bruker: InnloggetBruker, @PathVariable id: Long): ResponseEntity<ByteArray> {
        val bilde = skanninger.hentBilde(bruker.brukerId, id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(bilde.mimeType))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
            .body(bilde.data)
    }

    /** Lagrer den rettede listen som husstandens beholdning; erstatter den forrige. */
    @PostMapping("/{id}/beholdning")
    fun lagre(
        @AuthenticationPrincipal bruker: InnloggetBruker,
        @PathVariable id: Long,
        @Valid @RequestBody foresporsel: LagreForesporsel,
    ): ResponseEntity<Void> {
        skanninger.lagreSomBeholdning(
            bruker.brukerId,
            id,
            foresporsel.varer.map { RettetVare(it.navn, it.antall, it.aiIndeks) },
        )
        return ResponseEntity.noContent().build()
    }
}
