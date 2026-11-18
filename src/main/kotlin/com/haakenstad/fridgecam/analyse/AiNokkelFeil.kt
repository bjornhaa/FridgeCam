package com.haakenstad.fridgecam.analyse

import com.haakenstad.fridgecam.common.ApiException
import org.springframework.http.HttpStatus

/** Feil som skyldes husstandens nøkkel eller konto; frontend sender brukeren til Profil. */
object AiNokkelFeil {
    fun ugyldig() = ApiException(
        HttpStatus.CONFLICT,
        "ugyldig_ai_nokkel",
        "AI-tjenesten godtok ikke nøkkelen. Sjekk den under Profil.",
    )

    fun ingenKreditt() = ApiException(
        HttpStatus.PAYMENT_REQUIRED,
        "ai_mangler_kreditt",
        "Det er tomt for kreditt på AI-kontoen. Fyll på hos leverandøren.",
    )
}
