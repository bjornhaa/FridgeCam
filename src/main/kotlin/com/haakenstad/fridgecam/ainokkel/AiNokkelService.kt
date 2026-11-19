package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.analyse.AiLeverandor
import com.haakenstad.fridgecam.analyse.AiProperties
import com.haakenstad.fridgecam.common.ApiException
import com.haakenstad.fridgecam.husstand.HusstandTilgang
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Instant

data class NokkelInfo(val sisteTegn: String, val endret: Instant, val endretAv: String?)

/**
 * Husstandens AI-nøkler, én per leverandør (#42, #54): legges inn, byttes og fjernes
 * av alle i husstanden. Nøkkelen lagres bare kryptert, dekrypteres bare rett før
 * et AI-kall, og logges eller returneres aldri.
 */
@Service
class AiNokkelService(
    private val nokler: AiNokkelRepository,
    private val kryptering: NokkelKryptering,
    private val tilgang: HusstandTilgang,
    private val props: AiProperties,
    env: Environment,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val lokal = env.acceptsProfiles(Profiles.of("local"))

    fun lagre(brukerId: Long, leverandor: AiLeverandor, nokkel: String) {
        val husstandId = tilgang.husstandFor(brukerId)
        val ren = validerNokkel(leverandor, nokkel)
        nokler.lagre(husstandId, leverandor, kryptering.krypter(ren, husstandId, leverandor), ren.takeLast(4), brukerId)
        log.info("AI-nøkkel for {} lagt inn for husstand {} av bruker {}", leverandor, husstandId, brukerId)
    }

    fun fjern(brukerId: Long, leverandor: AiLeverandor) {
        val husstandId = tilgang.husstandFor(brukerId)
        nokler.slett(husstandId, leverandor)
        log.info("AI-nøkkel for {} fjernet for husstand {} av bruker {}", leverandor, husstandId, brukerId)
    }

    /** Det som kan vises om nøkkelen (aldri selve nøkkelen), eller null uten nøkkel. */
    fun info(husstandId: Long, leverandor: AiLeverandor): NokkelInfo? =
        nokler.finn(husstandId, leverandor)?.let { NokkelInfo(it.sisteTegn, it.endret, it.endretAv) }

    /** Bare lokalt: om nøkkelen fra .env brukes når husstanden ikke har lagt inn egen. */
    fun harReserve(leverandor: AiLeverandor): Boolean = reserveNokkel(props, leverandor, lokal) != null

    /**
     * Nøkkelen husstandens AI-kall hos [leverandor] skal bruke. Uten nøkkel:
     * `mangler_ai_nokkel`, og frontend sender brukeren til Profil.
     */
    fun nokkelFor(husstandId: Long, leverandor: AiLeverandor): AiNokkel {
        val lagret = nokler.finn(husstandId, leverandor)
            ?: return reserveNokkel(props, leverandor, lokal) ?: throw manglerNokkel(leverandor)
        return try {
            AiNokkel(kryptering.dekrypter(lagret.kryptert, husstandId, leverandor))
        } catch (e: Exception) {
            // Feil hovednøkkel eller endret rad. Unntaket inneholder ikke nøkkelen.
            log.error("Kunne ikke dekryptere AI-nøkkelen til husstand {}: {}", husstandId, e.javaClass.simpleName)
            throw ApiException(
                HttpStatus.CONFLICT,
                "ugyldig_ai_nokkel",
                "AI-nøkkelen kunne ikke leses. Legg den inn på nytt under Profil.",
            )
        }
    }

    companion object {
        private const val MAKS_LENGDE = 500
        private const val MIN_LENGDE = 20

        fun manglerNokkel(leverandor: AiLeverandor) = ApiException(
            HttpStatus.CONFLICT,
            "mangler_ai_nokkel",
            "Husstanden har ikke lagt inn en AI-nøkkel for ${leverandornavn(leverandor)}. Legg den inn under Profil.",
        )

        fun leverandornavn(leverandor: AiLeverandor) = when (leverandor) {
            AiLeverandor.ANTHROPIC -> "Anthropic"
            AiLeverandor.GEMINI -> "Google Gemini"
            AiLeverandor.FAKE -> "fake"
        }

        /**
         * Lokal reserve fra `.env`, så man kan utvikle uten å legge inn nøkkelen
         * i den flyktige databasen. Bare med `local`-profilen; i prod er
         * `ANTHROPIC_API_KEY`/`GEMINI_API_KEY` heller ikke koblet til (application.yml).
         */
        internal fun reserveNokkel(props: AiProperties, leverandor: AiLeverandor, lokal: Boolean): AiNokkel? {
            if (!lokal) return null
            val verdi = when (leverandor) {
                AiLeverandor.ANTHROPIC -> props.anthropicApiKey
                AiLeverandor.GEMINI -> props.geminiApiKey
                AiLeverandor.FAKE -> null
            }
            return verdi?.trim()?.takeIf { it.isNotEmpty() }?.let(::AiNokkel)
        }

        /**
         * Sjekker formen på nøkkelen, så en feil innliming (mellomrom, feil
         * leverandør) oppdages med en gang. Meldingene gjentar aldri nøkkelen.
         * Admin-nøkler fra Anthropic avvises: de kan administrere hele
         * organisasjonen og skal ikke ligge i en app.
         */
        internal fun validerNokkel(leverandor: AiLeverandor, nokkel: String): String {
            val ren = nokkel.trim()
            fun ugyldig(melding: String) = ApiException(HttpStatus.BAD_REQUEST, "ugyldig_foresporsel", melding)
            if (ren.isEmpty()) throw ugyldig("Lim inn nøkkelen.")
            if (ren.length > MAKS_LENGDE) throw ugyldig("Nøkkelen er for lang.")
            if (ren.any { it.code !in 0x21..0x7E }) {
                throw ugyldig("Nøkkelen kan bare inneholde vanlige tegn, uten mellomrom.")
            }
            if (leverandor == AiLeverandor.ANTHROPIC) {
                if (ren.startsWith("sk-ant-admin")) {
                    throw ugyldig("Dette er en admin-nøkkel. Lag en vanlig API-nøkkel i stedet.")
                }
                if (!ren.startsWith("sk-ant-")) throw ugyldig("Nøkler fra Anthropic begynner med «sk-ant-».")
            }
            if (ren.length < MIN_LENGDE) throw ugyldig("Nøkkelen er for kort.")
            return ren
        }
    }
}
