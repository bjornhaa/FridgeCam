package com.haakenstad.fridgecam.innlogging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Leser `Authorization: Bearer …` og setter innlogget bruker. Et ugyldig
 * token gir ingen feil her; forespørselen går videre som anonym, og
 * SecurityConfig avviser den hvis stien krever innlogging.
 *
 * Er ikke en Spring-bean: da ville Spring Boot også registrert den som et
 * vanlig servlet-filter, utenom sikkerhetskjeden.
 */
class JwtFilter(private val jwt: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val brukerId = jwt.lesBrukerId(header.removePrefix("Bearer ").trim())
            if (brukerId != null) {
                val kontekst = SecurityContextHolder.createEmptyContext()
                kontekst.authentication = JwtAutentisering(InnloggetBruker(brukerId))
                SecurityContextHolder.setContext(kontekst)
            }
        }
        chain.doFilter(request, response)
    }
}
