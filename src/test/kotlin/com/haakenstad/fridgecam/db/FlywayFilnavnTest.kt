package com.haakenstad.fridgecam.db

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Ren filnavnsjekk på begge migreringstrærne (som i talik-ai). Ingen database.
 * Skjemainnholdet sammenlignes i [FlywaySkjemaTest].
 */
class FlywayFilnavnTest {

    private val trar = listOf("migration", "migration-embedded")
    private val versjonsnavn = Regex("""^V(\d+)__[a-z0-9_]+\.sql$""")

    private fun filer(tre: String): List<String> =
        File("src/main/resources/db/$tre").listFiles()!!
            .map { it.name }
            .filter { it.endsWith(".sql") }

    private fun versjoner(tre: String): List<Int> =
        filer(tre).mapNotNull { versjonsnavn.find(it)?.groupValues?.get(1)?.toInt() }

    @Test
    fun `alle migreringsfiler følger navnekonvensjonen`() {
        for (tre in trar) {
            val avvik = filer(tre).filterNot { versjonsnavn.matches(it) }
            assertThat(avvik)
                .withFailMessage("$tre har filnavn som ikke er V{n}__navn.sql: %s", avvik)
                .isEmpty()
        }
    }

    /** To filer med samme versjon får Flyway til å nekte å starte. */
    @Test
    fun `ingen to migreringer deler versjonsnummer`() {
        for (tre in trar) {
            val duplikater = versjoner(tre).groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            assertThat(duplikater)
                .withFailMessage("$tre har flere migreringer med samme versjon: %s", duplikater)
                .isEmpty()
        }
    }

    /**
     * Et hull betyr som regel at en migrering ligger i en annen gren med lavere
     * nummer. Deployes den etterpå, avviser Flyway den, og appen starter ikke.
     */
    @Test
    fun `versjonene er sammenhengende fra 1`() {
        for (tre in trar) {
            val v = versjoner(tre).sorted()
            assertThat(v)
                .withFailMessage("hull i versjonsrekka for db/$tre: %s", v)
                .isEqualTo((1..v.size).toList())
        }
    }
}
