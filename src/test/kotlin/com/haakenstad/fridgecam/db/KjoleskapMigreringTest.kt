package com.haakenstad.fridgecam.db

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/**
 * V6 (#61) flytter eksisterende skanninger og beholdning til ett standard
 * kjøleskap per husstand. Kjøres mot en base med data fra før V6, som i prod.
 */
class KjoleskapMigreringTest {

    @Test
    fun `eksisterende data havner i husstandens eget kjøleskap «Hjemme»`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            val flyway = { maal: String ->
                Flyway.configure().dataSource(postgres.postgresDatabase)
                    .locations("classpath:db/migration").target(maal).load()
            }
            flyway("5").migrate()
            val jdbc = NamedParameterJdbcTemplate(postgres.postgresDatabase)
            val husstander = listOf(nyHusstandMedData(jdbc), nyHusstandMedData(jdbc), tomHusstand(jdbc))

            flyway("latest").migrate()

            val kjoleskap = jdbc.queryForList(
                "SELECT husstand_id, navn FROM kjoleskap ORDER BY husstand_id", emptyMap<String, Any>(),
            )
            assertThat(kjoleskap.map { it["husstand_id"] }).containsExactlyElementsOf(husstander)
            assertThat(kjoleskap.map { it["navn"] }).containsOnly("Hjemme")

            // Hver rad peker på kjøleskapet i sin egen husstand.
            for (tabell in listOf("skanning", "beholdning_vare")) {
                val feil = jdbc.queryForObject(
                    """
                    SELECT count(*) FROM $tabell t
                    LEFT JOIN kjoleskap k ON k.id = t.kjoleskap_id AND k.husstand_id = t.husstand_id
                    WHERE k.id IS NULL
                    """.trimIndent(),
                    emptyMap<String, Any>(), Int::class.java,
                )
                val antall = jdbc.queryForObject("SELECT count(*) FROM $tabell", emptyMap<String, Any>(), Int::class.java)
                assertThat(antall).`as`(tabell).isEqualTo(2)
                assertThat(feil).`as`(tabell).isZero()
            }
        }
    }

    private fun tomHusstand(jdbc: NamedParameterJdbcTemplate): Long =
        jdbc.queryForObject("INSERT INTO husstand (navn) VALUES ('Hjemme') RETURNING id", emptyMap<String, Any>(), Long::class.java)!!

    private fun nyHusstandMedData(jdbc: NamedParameterJdbcTemplate): Long {
        val h = tomHusstand(jdbc)
        val s = jdbc.queryForObject(
            "INSERT INTO skanning (husstand_id, leverandor, ai_varer) VALUES (:h, 'fake', '[]') RETURNING id",
            mapOf("h" to h), Long::class.java,
        )!!
        jdbc.update(
            "INSERT INTO beholdning_vare (husstand_id, skanning_id, navn, antall) VALUES (:h, :s, 'melk', 1)",
            mapOf("h" to h, "s" to s),
        )
        return h
    }
}
