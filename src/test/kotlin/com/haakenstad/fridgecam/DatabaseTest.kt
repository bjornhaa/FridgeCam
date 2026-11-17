package com.haakenstad.fridgecam

import com.haakenstad.fridgecam.bruker.BrukerRepository
import com.haakenstad.fridgecam.husstand.HusstandRepository
import com.haakenstad.fridgecam.husstand.Rolle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/**
 * For repository-tester mot ekte Postgres. Arver [AppTest] for å dele kontekst
 * og database med de andre testene; hver test lager derfor sin egen husstand.
 */
abstract class DatabaseTest : AppTest() {

    @Autowired protected lateinit var jdbc: NamedParameterJdbcTemplate
    @Autowired private lateinit var brukere: BrukerRepository
    @Autowired private lateinit var husstander: HusstandRepository

    protected data class Eier(val brukerId: Long, val husstandId: Long)

    /** En ny bruker som eier en ny husstand. */
    protected fun nyEier(): Eier {
        val bruker = brukere.opprett(unikEpost(), "Test", "x")
        val husstand = husstander.opprett("Hjemme")
        husstander.leggTilMedlem(husstand, bruker.id, Rolle.EIER)
        return Eier(bruker.id, husstand)
    }
}
