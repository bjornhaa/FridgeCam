package com.haakenstad.fridgecam.config

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Sender 404 utenfor `/api/` og `/actuator/` videre til frontendens
 * `index.html`, slik at dype lenker i appen fungerer ved oppfrisking.
 * Feil fra API og actuator svarer fortsatt med JSON.
 */
@Controller
class SpaController : ErrorController {

    @RequestMapping("/error")
    fun handleError(request: HttpServletRequest, response: HttpServletResponse): Any {
        val status = (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int)
            ?: HttpStatus.INTERNAL_SERVER_ERROR.value()
        val path = (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) as? String).orEmpty()

        val erApiEllerActuator = path.startsWith("/api/") || path.startsWith("/actuator/")

        if (status == HttpStatus.NOT_FOUND.value() && !erApiEllerActuator) {
            // Nettleseren skal se index.html som et vanlig 200-svar.
            response.status = HttpStatus.OK.value()
            return "forward:/index.html"
        }
        return ResponseEntity.status(status)
            .body(mapOf("status" to status, "path" to path))
    }
}
