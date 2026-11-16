package com.haakenstad.fridgecam.innlogging

import org.springframework.security.authentication.AbstractAuthenticationToken

/** Den innloggede brukeren, hentes i controllere med `@AuthenticationPrincipal`. */
data class InnloggetBruker(val brukerId: Long)

class JwtAutentisering(
    private val bruker: InnloggetBruker,
) : AbstractAuthenticationToken(emptyList()) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): InnloggetBruker = bruker
}
