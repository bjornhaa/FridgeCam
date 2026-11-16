package com.haakenstad.fridgecam.analyse

import com.haakenstad.fridgecam.common.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

data class AnalyseSvar(
    val varer: List<FunnetVare>,
    val leverandor: String,
)

@RestController
@RequestMapping("/api")
class AnalyseController(
    private val visionProvider: VisionProvider,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Tar imot et bilde av kjøleskapet (multipart-felt `bilde`) og returnerer
     * varene AI-en fant (#7). Bildet er allerede krympet i nettleseren, så
     * det er typisk noen hundre kB.
     */
    @PostMapping("/analyze", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun analyser(@RequestPart("bilde") bilde: MultipartFile): AnalyseSvar {
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
        val varer = visionProvider.analyser(data, mimeType)
        log.info(
            "Analyse: {} kB {}, {} varer fra {} på {} ms",
            data.size / 1024, mimeType, varer.size, visionProvider.navn, (System.nanoTime() - start) / 1_000_000,
        )
        return AnalyseSvar(varer, visionProvider.navn)
    }
}
