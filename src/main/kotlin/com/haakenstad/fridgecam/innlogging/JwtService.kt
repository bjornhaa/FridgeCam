package com.haakenstad.fridgecam.innlogging

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date

/**
 * `fridgecam.jwt.*`. Hemmeligheten kommer fra `JWT_SECRET` (Fly secret i prod);
 * local-profilen har en egen utviklingsverdi.
 */
@ConfigurationProperties("fridgecam.jwt")
data class JwtEgenskaper(
    val hemmelighet: String = "",
    val levetid: Duration = Duration.ofDays(30),
)

/**
 * Lager og leser JWT-er (HS256). Tokenet inneholder bare bruker-id-en (`sub`);
 * husstand og navn slås opp i databasen når de trengs.
 */
@Component
class JwtService(private val egenskaper: JwtEgenskaper) {

    private val nokkel = run {
        val bytes = egenskaper.hemmelighet.toByteArray(Charsets.UTF_8)
        check(bytes.isNotEmpty()) {
            "JWT_SECRET er ikke satt. Lag en med f.eks. `openssl rand -base64 48`."
        }
        // HS256 krever minst 256 bit; jjwt kaster ellers en mindre forståelig feil.
        check(bytes.size >= 32) { "JWT_SECRET er for kort: må være minst 32 tegn." }
        Keys.hmacShaKeyFor(bytes)
    }

    fun lag(brukerId: Long): String {
        val na = System.currentTimeMillis()
        return Jwts.builder()
            .subject(brukerId.toString())
            .issuedAt(Date(na))
            .expiration(Date(na + egenskaper.levetid.toMillis()))
            .signWith(nokkel)
            .compact()
    }

    /** Bruker-id-en i tokenet, eller null hvis det er ugyldig, utløpt eller signert med en annen nøkkel. */
    fun lesBrukerId(token: String): Long? =
        try {
            Jwts.parser().verifyWith(nokkel).build()
                .parseSignedClaims(token).payload.subject?.toLongOrNull()
        } catch (e: JwtException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
