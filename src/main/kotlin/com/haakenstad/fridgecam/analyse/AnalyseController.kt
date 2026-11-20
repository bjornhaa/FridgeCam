package com.haakenstad.fridgecam.analyse

import com.haakenstad.fridgecam.common.ApiException
import com.haakenstad.fridgecam.innlogging.InnloggetBruker
import com.haakenstad.fridgecam.skanning.SkanningService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.Locale

data class AnalyseSvar(
    /** Skanningen er lagret; den rettede listen sendes til `/api/skanninger/{id}/beholdning`. */
    val skanningId: Long,
    val varer: List<FunnetVare>,
    val leverandor: String,
)

@RestController
@RequestMapping("/api")
class AnalyseController(private val skanninger: SkanningService) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Tar imot et bilde av kjøleskapet (multipart-felt `bilde`) og returnerer
     * varene AI-en fant (#7). Bildet er allerede krympet i nettleseren, så
     * det er typisk noen hundre kB. `kjoleskapId` (skjemafelt) velger kjøleskapet;
     * uten går skanningen til husstandens standard kjøleskap.
     */
    @PostMapping("/analyze", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun analyser(
        @AuthenticationPrincipal bruker: InnloggetBruker,
        @RequestPart("bilde") bilde: MultipartFile,
        @RequestParam("kjoleskapId", required = false) kjoleskapId: Long?,
    ): AnalyseSvar {
        if (bilde.isEmpty) {
            throw ApiException(HttpStatus.BAD_REQUEST, "bilde_mangler", "Bildet er tomt.")
        }
        val data = bilde.bytes
        val mimeType = Bildeformat.finn(data)
            ?: throw ApiException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "ugyldig_bildeformat",
                "Bildet må være JPEG, PNG eller WebP.",
            )

        val start = System.nanoTime()
        val resultat = skanninger.analyser(bruker.brukerId, kjoleskapId, data, mimeType)
        log.info(
            "Analyse: {} kB {}, {} varer fra {} på {} ms{}",
            data.size / 1024, mimeType, resultat.varer.size, resultat.leverandor,
            (System.nanoTime() - start) / 1_000_000, beskriv(resultat.forbruk),
        )
        return AnalyseSvar(resultat.skanningId, resultat.varer, resultat.leverandor)
    }

    private fun beskriv(forbruk: Tokenforbruk?): String {
        if (forbruk == null) return ""
        val kostnad = forbruk.kostnadUsd?.let { " ≈ $%.4f".format(Locale.ROOT, it) } ?: ""
        return " (${forbruk.modell}: ${forbruk.inn} inn / ${forbruk.ut} ut tokens$kostnad)"
    }
}
