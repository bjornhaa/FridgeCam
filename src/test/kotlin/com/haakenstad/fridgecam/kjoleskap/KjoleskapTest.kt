package com.haakenstad.fridgecam.kjoleskap

import com.haakenstad.fridgecam.AppTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/** Legge til, gi nytt navn og slette kjøleskap (#62), mot hele appen. */
class KjoleskapTest : AppTest() {

    @Autowired private lateinit var jdbc: NamedParameterJdbcTemplate

    private fun opprett(token: String, navn: String): ResultActionsDsl =
        mvc.post("/api/kjoleskap") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"navn":"$navn"}"""
        }

    private fun giNyttNavn(token: String, id: Long, navn: String): ResultActionsDsl =
        mvc.put("/api/kjoleskap/$id") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"navn":"$navn"}"""
        }

    private fun slett(token: String, id: Long): ResultActionsDsl =
        mvc.delete("/api/kjoleskap/$id") { header("Authorization", "Bearer $token") }

    private fun ider(token: String): List<Long> {
        val svar = mvc.get("/api/kjoleskap") { header("Authorization", "Bearer $token") }
            .andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").findAll(svar).map { it.groupValues[1].toLong() }.toList()
    }

    @Test
    fun `nytt kjøleskap legges til etter de eksisterende`() {
        val token = registrer()

        opprett(token, "  Hytta  ").andExpect {
            status { isCreated() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].navn") { value("Hjemme") }
            jsonPath("$[1].navn") { value("Hytta") }
            jsonPath("$[1].sistSkannet") { value(null) }
        }
    }

    @Test
    fun `navnet må være unikt i husstanden, uavhengig av store og små bokstaver`() {
        val token = registrer()

        opprett(token, "hjemme").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("kjoleskap_finnes") }
        }
        // En annen husstand kan godt ha samme navn.
        opprett(registrer(), "Hytta").andExpect { status { isCreated() } }
        opprett(token, "Hytta").andExpect { status { isCreated() } }
    }

    @Test
    fun `tomt eller for langt navn avvises med norsk melding`() {
        val token = registrer()

        opprett(token, "   ").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Kjøleskapet må ha et navn.") }
        }
        opprett(token, "x".repeat(41)).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Navnet kan ha høyst 40 tegn.") }
        }
    }

    @Test
    fun `nytt navn`() {
        val token = registrer()
        val hjemme = ider(token).single()

        giNyttNavn(token, hjemme, "Byen").andExpect {
            status { isOk() }
            jsonPath("$[0].navn") { value("Byen") }
        }
        // Samme navn med annen skrivemåte er lov på samme kjøleskap.
        giNyttNavn(token, hjemme, "BYEN").andExpect { status { isOk() } }
        opprett(token, "Hytta")
        giNyttNavn(token, hjemme, "hytta").andExpect { status { isConflict() } }
    }

    @Test
    fun `sletting fjerner beholdning, skanninger og bilde, men ikke de andre kjøleskapene`() {
        val token = registrer()
        val hjemme = ider(token).single()
        opprett(token, "Hytta")
        val hytta = ider(token)[1]
        val skanning = Regex("\"skanningId\":(\\d+)").find(
            mvc.multipart("/api/analyze") {
                header("Authorization", "Bearer $token")
                file(MockMultipartFile("bilde", "k.jpg", "image/jpeg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
                param("kjoleskapId", hytta.toString())
            }.andReturn().response.contentAsString,
        )!!.groupValues[1].toLong()

        slett(token, hytta).andExpect { status { isNoContent() } }

        assertThat(ider(token)).containsExactly(hjemme)
        val igjen = jdbc.queryForObject(
            "SELECT count(*) FROM skanning WHERE id = :s", mapOf("s" to skanning), Int::class.java,
        )
        assertThat(igjen).isZero()
    }

    @Test
    fun `det siste kjøleskapet kan ikke slettes`() {
        val token = registrer()

        slett(token, ider(token).single()).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("siste_kjoleskap") }
        }
    }

    @Test
    fun `andres kjøleskap kan ikke endres eller slettes`() {
        val eier = registrer()
        opprett(eier, "Hytta")
        val hytta = ider(eier)[1]
        val fremmed = registrer()

        giNyttNavn(fremmed, hytta, "Mitt").andExpect { status { isNotFound() } }
        slett(fremmed, hytta).andExpect { status { isNotFound() } }
        assertThat(ider(eier)).contains(hytta)
    }

    @Test
    fun `høyst 20 kjøleskap per husstand`() {
        val token = registrer()
        (2..KjoleskapService.MAKS).forEach { opprett(token, "Skap $it").andExpect { status { isCreated() } } }

        opprett(token, "Ett til").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("for_mange_kjoleskap") }
        }
    }

    @Test
    fun `krever innlogging`() {
        mvc.post("/api/kjoleskap") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"navn":"Hytta"}"""
        }.andExpect { status { isUnauthorized() } }
        mvc.delete("/api/kjoleskap/1").andExpect { status { isUnauthorized() } }
    }
}
