package com.haakenstad.fridgecam.innlogging

import com.haakenstad.fridgecam.bruker.Bruker
import com.haakenstad.fridgecam.bruker.BrukerRepository
import com.haakenstad.fridgecam.common.ApiException
import com.haakenstad.fridgecam.husstand.HusstandRepository
import com.haakenstad.fridgecam.husstand.Rolle
import com.haakenstad.fridgecam.kjoleskap.KjoleskapRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InnloggingService(
    private val brukere: BrukerRepository,
    private val husstander: HusstandRepository,
    private val kjoleskap: KjoleskapRepository,
    private val passordKoder: PasswordEncoder,
    private val jwt: JwtService,
) {

    /**
     * Brukes når e-posten ikke finnes, så innlogging tar like lang tid uansett
     * og svartiden ikke røper hvilke e-poster som er registrert.
     */
    private val dummyHash = hash("ikke-et-ekte-passord")

    /** Oppretter bruker, en husstand med brukeren som eier og et kjøleskap, i samme transaksjon. */
    @Transactional
    fun registrer(foresporsel: RegistrerForesporsel): InnloggingSvar {
        if (foresporsel.passord.toByteArray(Charsets.UTF_8).size > MAKS_PASSORD_BYTES) {
            throw ApiException(HttpStatus.BAD_REQUEST, "ugyldig_foresporsel", "Passordet er for langt.")
        }
        val epost = foresporsel.epost.trim().lowercase()
        if (brukere.finnMedEpost(epost) != null) {
            throw epostIBruk()
        }
        val bruker = try {
            brukere.opprett(epost, foresporsel.navn.trim(), hash(foresporsel.passord))
        } catch (e: DuplicateKeyException) {
            // To registreringer med samme e-post samtidig.
            throw epostIBruk()
        }
        val husstandId = husstander.opprett("Hjemme")
        husstander.leggTilMedlem(husstandId, bruker.id, Rolle.EIER)
        kjoleskap.opprett(husstandId, "Hjemme")
        return InnloggingSvar(jwt.lag(bruker.id), meg(bruker))
    }

    @Transactional(readOnly = true)
    fun loggInn(foresporsel: LoggInnForesporsel): InnloggingSvar {
        val bruker = brukere.finnMedEpost(foresporsel.epost.trim())
        val passordStemmer = when {
            foresporsel.passord.toByteArray(Charsets.UTF_8).size > MAKS_PASSORD_BYTES -> false
            bruker == null -> {
                passordKoder.matches(foresporsel.passord, dummyHash)
                false
            }
            else -> passordKoder.matches(foresporsel.passord, bruker.passordHash)
        }
        if (bruker == null || !passordStemmer) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "ugyldig_innlogging", "Feil e-post eller passord.")
        }
        return InnloggingSvar(jwt.lag(bruker.id), meg(bruker))
    }

    @Transactional(readOnly = true)
    fun meg(brukerId: Long): MegSvar {
        // Gyldig token, men brukeren er slettet.
        val bruker = brukere.finn(brukerId)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "ikke_innlogget", "Du må logge inn.")
        return meg(bruker)
    }

    private fun meg(bruker: Bruker) = MegSvar(
        id = bruker.id,
        epost = bruker.epost,
        navn = bruker.navn,
        husstander = husstander.forBruker(bruker.id).map { HusstandSvar(it.husstandId, it.navn, it.rolle) },
    )

    private fun hash(passord: String): String =
        requireNotNull(passordKoder.encode(passord)) { "PasswordEncoder ga ingen hash" }

    private fun epostIBruk() =
        ApiException(HttpStatus.CONFLICT, "epost_i_bruk", "Det finnes allerede en bruker med denne e-postadressen.")

    companion object {
        /** BCrypt ser bare på de første 72 bytene, og Spring Security avviser lengre passord. */
        const val MAKS_PASSORD_BYTES = 72
    }
}
