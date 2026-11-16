package com.haakenstad.fridgecam.db

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/**
 * Kjører migreringene i `db/migration` mot ekte Postgres (Zonky, ingen Docker)
 * og sjekker at reglene i skjemaet faktisk holder.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigreringTest {

    private lateinit var postgres: EmbeddedPostgres
    private lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeAll
    fun startDatabase() {
        postgres = EmbeddedPostgres.builder().start()
        Flyway.configure()
            .dataSource(postgres.postgresDatabase)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        jdbc = NamedParameterJdbcTemplate(postgres.postgresDatabase)
    }

    @AfterAll
    fun stoppDatabase() {
        postgres.close()
    }

    @BeforeEach
    fun tomTabeller() {
        jdbc.jdbcTemplate.execute("TRUNCATE bruker, husstand CASCADE")
    }

    @Test
    fun `grunntabellene finnes`() {
        val tabeller = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            emptyMap<String, Any>(),
            String::class.java,
        )

        assertThat(tabeller).contains("bruker", "husstand", "husstand_medlem")
    }

    @Test
    fun `e-post er unik uavhengig av store og små bokstaver`() {
        nyBruker("ola@example.com")

        assertThatThrownBy { nyBruker("OLA@Example.com") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `rolle må være EIER eller MEDLEM`() {
        val bruker = nyBruker("kari@example.com")
        val husstand = nyHusstand()

        assertThatThrownBy { leggTilMedlem(husstand, bruker, "SJEF") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `samme bruker kan være med i flere husstander, men bare én gang i hver`() {
        val bruker = nyBruker("per@example.com")
        val hjemme = nyHusstand()
        val hytta = nyHusstand()

        leggTilMedlem(hjemme, bruker, "EIER")
        leggTilMedlem(hytta, bruker, "MEDLEM")

        assertThatThrownBy { leggTilMedlem(hjemme, bruker, "MEDLEM") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `sletting av husstand fjerner medlemskap, men ikke brukeren`() {
        val bruker = nyBruker("lise@example.com")
        val husstand = nyHusstand()
        leggTilMedlem(husstand, bruker, "EIER")

        jdbc.update("DELETE FROM husstand WHERE id = :id", mapOf("id" to husstand))

        assertThat(antall("husstand_medlem")).isZero()
        assertThat(antall("bruker")).isEqualTo(1)
    }

    private fun nyBruker(epost: String): Long =
        jdbc.queryForObject(
            "INSERT INTO bruker (epost, navn, passord_hash) VALUES (:epost, 'Test', 'x') RETURNING id",
            mapOf("epost" to epost),
            Long::class.java,
        )!!

    private fun nyHusstand(): Long =
        jdbc.queryForObject(
            "INSERT INTO husstand (navn) VALUES ('Hjemme') RETURNING id",
            emptyMap<String, Any>(),
            Long::class.java,
        )!!

    private fun leggTilMedlem(husstand: Long, bruker: Long, rolle: String) {
        jdbc.update(
            "INSERT INTO husstand_medlem (husstand_id, bruker_id, rolle) VALUES (:h, :b, :r)",
            mapOf("h" to husstand, "b" to bruker, "r" to rolle),
        )
    }

    private fun antall(tabell: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tabell", emptyMap<String, Any>(), Int::class.java)!!
}
