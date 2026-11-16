package com.haakenstad.fridgecam.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException

data class ApiFeil(val code: String, val message: String)

/**
 * Felles feilformat for API-et: `{ "code": …, "message": … }`.
 *
 * Gjelder bare våre egne controllere (basePackages). Uten den avgrensningen
 * ville catch-all-handleren under også fanget Springs 404 for statiske filer,
 * og dype lenker i appen ville gitt 500 i stedet for index.html (SpaController).
 */
@RestControllerAdvice(basePackages = ["com.haakenstad.fridgecam"])
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException): ResponseEntity<ApiFeil> =
        svar(e.status, e.code, e.message)

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun manglerDel(e: MissingServletRequestPartException): ResponseEntity<ApiFeil> =
        svar(HttpStatus.BAD_REQUEST, "bilde_mangler", "Forespørselen mangler bilde (felt «${e.requestPartName}»).")

    @ExceptionHandler(MultipartException::class)
    fun multipart(e: MultipartException): ResponseEntity<ApiFeil> =
        svar(HttpStatus.BAD_REQUEST, "bilde_mangler", "Forventet en multipart-forespørsel med et bilde.")

    @ExceptionHandler(Exception::class)
    fun uventet(e: Exception): ResponseEntity<ApiFeil> {
        log.error("Uventet feil", e)
        return svar(HttpStatus.INTERNAL_SERVER_ERROR, "intern_feil", "Noe gikk galt. Prøv igjen.")
    }

}

/**
 * For stor opplasting oppdages mens multipart-forespørselen pakkes ut, før
 * Spring vet hvilken controller den skal til. En advice avgrenset med
 * basePackages treffer da ikke, så denne gjelder hele appen. Den håndterer
 * bare denne ene feilen og kommer derfor ikke i veien for SpaController.
 */
@RestControllerAdvice
class OpplastingFeilHandler {

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun forStort(e: MaxUploadSizeExceededException): ResponseEntity<ApiFeil> =
        svar(HttpStatus.CONTENT_TOO_LARGE, "bilde_for_stort", "Bildet er for stort.")
}

private fun svar(status: HttpStatus, code: String, message: String) =
    ResponseEntity.status(status).body(ApiFeil(code, message))
