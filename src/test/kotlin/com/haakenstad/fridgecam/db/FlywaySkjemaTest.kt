package com.haakenstad.fridgecam.db

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import javax.sql.DataSource

/**
 * Sammenligner skjemaet de to migreringstrærne gir (som i talik-ai).
 *
 * `db/migration/` er prod-historikken, `db/migration-embedded/` det samlede
 * skjemaet local-profilen bygger fra, pluss testdata. En kolonne lagt til bare
 * ett sted gir en app som virker lokalt og feiler i prod, eller omvendt. Prod
 * kommer dit med ALTER-setninger, lokalt med én CREATE, så eneste pålitelige
 * svar er å kjøre begge og sammenligne.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywaySkjemaTest {

    private lateinit var postgres: EmbeddedPostgres
    private lateinit var prod: JdbcTemplate
    private lateinit var lokal: JdbcTemplate

    /** Tabeller som med vilje bare finnes i prod mens slettingen venter på deploy. */
    private val utfasede = emptySet<String>()

    @BeforeAll
    fun migrerBeggeTrar() {
        postgres = EmbeddedPostgres.builder().start()
        val admin = JdbcTemplate(kobling("postgres"))
        admin.execute("CREATE DATABASE tre_prod")
        admin.execute("CREATE DATABASE tre_lokal")

        val prodDs = kobling("tre_prod")
        val lokalDs = kobling("tre_lokal")
        Flyway.configure().dataSource(prodDs).locations("classpath:db/migration").load().migrate()
        Flyway.configure().dataSource(lokalDs).locations("classpath:db/migration-embedded").load().migrate()
        prod = JdbcTemplate(prodDs)
        lokal = JdbcTemplate(lokalDs)
    }

    private fun kobling(db: String): DataSource =
        SimpleDriverDataSource(org.postgresql.Driver(), postgres.getJdbcUrl("postgres", db), "postgres", "")

    @AfterAll
    fun stopp() = postgres.close()

    private fun kolonner(jdbc: JdbcTemplate): Map<String, String> =
        jdbc.query(
            """
            SELECT table_name, column_name, data_type, is_nullable, column_default, is_identity
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
            """.trimIndent(),
        ) { rs, _ ->
            rs.getString(1) to "${rs.getString(2)} ${rs.getString(3)} null=${rs.getString(4)} " +
                "default=${rs.getString(5)} identity=${rs.getString(6)}"
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sorted().joinToString("\n") }

    /** Navn kan avvike mellom trærne; definisjonene skal ikke. */
    private fun constraints(jdbc: JdbcTemplate): Map<String, List<String>> =
        jdbc.query(
            """
            SELECT c.relname, pg_get_constraintdef(con.oid)
            FROM pg_constraint con
            JOIN pg_class c ON c.oid = con.conrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relname <> 'flyway_schema_history'
            """.trimIndent(),
        ) { rs, _ -> rs.getString(1) to rs.getString(2) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sorted() }

    /**
     * Indekser, uten navnet. Regler som «e-post er unik uavhengig av store/små
     * bokstaver» er en unik indeks, ikke en constraint, og ville ellers sluppet unna.
     */
    private fun indekser(jdbc: JdbcTemplate): Map<String, List<String>> =
        jdbc.query(
            """
            SELECT tablename, regexp_replace(indexdef, 'INDEX \S+ ON', 'INDEX ON')
            FROM pg_indexes
            WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
            """.trimIndent(),
        ) { rs, _ -> rs.getString(1) to rs.getString(2) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sorted() }

    @Test
    fun `begge trærne har de samme tabellene`() {
        val prodTabeller = kolonner(prod).keys - utfasede
        val lokaleTabeller = kolonner(lokal).keys
        assertThat(lokaleTabeller)
            .withFailMessage(
                "Mangler lokalt: %s. Bare lokalt: %s",
                prodTabeller - lokaleTabeller,
                lokaleTabeller - kolonner(prod).keys,
            )
            .isEqualTo(prodTabeller)
    }

    @Test
    fun `kolonnene er like i begge trærne`() = sammenlign("Kolonneavvik", kolonner(prod), kolonner(lokal))

    @Test
    fun `constraints er like i begge trærne`() = sammenlign("Constraint-avvik", constraints(prod), constraints(lokal))

    @Test
    fun `indeksene er like i begge trærne`() = sammenlign("Indeksavvik", indekser(prod), indekser(lokal))

    @Test
    fun `testbrukeren finnes bare lokalt`() {
        val sql = "SELECT count(*) FROM bruker WHERE epost = 'bjorn@haakenstad.com'"
        assertThat(lokal.queryForObject(sql, Int::class.java)).isEqualTo(1)
        assertThat(prod.queryForObject(sql, Int::class.java)).isZero()
    }

    private fun sammenlign(tittel: String, p: Map<String, Any>, l: Map<String, Any>) {
        val avvik = (p.keys - utfasede).union(l.keys).filter { p[it] != l[it] }
        val forklaring = avvik.joinToString("\n\n") { t -> "TABELL $t\n  prod:  ${p[t]}\n  lokal: ${l[t]}" }
        assertThat(avvik).withFailMessage("$tittel mellom trærne:\n%s", forklaring).isEmpty()
    }
}
