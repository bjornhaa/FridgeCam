package com.haakenstad.fridgecam.skanning

import com.haakenstad.fridgecam.AppTest
import com.haakenstad.fridgecam.analyse.FakeVisionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post

/** Skann → rett → lagre som beholdning (#32), mot hele appen og ekte database. */
class LagreSkanningTest : AppTest() {

    @Autowired private lateinit var jdbc: NamedParameterJdbcTemplate

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)

    /** Skanner med fake-AI-en og returnerer skanning-id-en. */
    private fun skann(token: String): Long {
        val svar = mvc.multipart("/api/analyze") {
            header("Authorization", "Bearer $token")
            file(MockMultipartFile("bilde", "k.jpg", "image/jpeg", jpeg))
        }.andReturn().response.contentAsString
        return Regex("\"skanningId\":(\\d+)").find(svar)!!.groupValues[1].toLong()
    }

    private fun lagre(token: String, skanningId: Long, varer: String): ResultActionsDsl =
        mvc.post("/api/skanninger/$skanningId/beholdning") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"varer":$varer}"""
        }

    private fun beholdning(token: String): ResultActionsDsl =
        mvc.get("/api/beholdning") { header("Authorization", "Bearer $token") }

    @Test
    fun `rettet liste blir beholdningen, med bokser bare for AI-ens varer`() {
        val token = registrer()
        val id = skann(token)

        // melk (indeks 0) rettet til 2 stk, egg (1) fjernet, «saft» lagt til.
        lagre(token, id, """[{"navn":"lettmelk","antall":2,"aiIndeks":0},{"navn":" saft ","antall":1}]""")
            .andExpect { status { isNoContent() } }

        beholdning(token).andExpect {
            status { isOk() }
            jsonPath("$.varer.length()") { value(2) }
            jsonPath("$.varer[0].navn") { value("lettmelk") }
            jsonPath("$.varer[0].antall") { value(2) }
            jsonPath("$.varer[0].bokser[0].x1") { value(FakeVisionProvider.VARER[0].bokser[0].x1) }
            jsonPath("$.varer[1].navn") { value("saft") }
            jsonPath("$.varer[1].bokser.length()") { value(0) }
            jsonPath("$.skanning.id") { value(id) }
            jsonPath("$.skanning.tidspunkt") { exists() }
        }
    }

    @Test
    fun `den rettede listen lagres på skanningen for senere måling`() {
        val token = registrer()
        val id = skann(token)

        lagre(token, id, """[{"navn":"lettmelk","antall":2,"aiIndeks":0}]""")

        val rettet = jdbc.queryForObject(
            "SELECT rettede_varer::text FROM skanning WHERE id = :id AND lagret IS NOT NULL",
            mapOf("id" to id),
            String::class.java,
        )
        assertThat(rettet).contains("\"navn\": \"lettmelk\"").contains("\"aiIndeks\": 0")
    }

    @Test
    fun `ny skanning erstatter beholdningen`() {
        val token = registrer()
        lagre(token, skann(token), """[{"navn":"melk","antall":1,"aiIndeks":0},{"navn":"egg","antall":6,"aiIndeks":1}]""")

        val ny = skann(token)
        lagre(token, ny, """[{"navn":"juice","antall":1}]""")

        beholdning(token).andExpect {
            jsonPath("$.varer.length()") { value(1) }
            jsonPath("$.varer[0].navn") { value("juice") }
            jsonPath("$.skanning.id") { value(ny) }
        }
    }

    @Test
    fun `tom liste tømmer kjøleskapet`() {
        val token = registrer()
        lagre(token, skann(token), """[{"navn":"melk","antall":1}]""")

        lagre(token, skann(token), "[]").andExpect { status { isNoContent() } }

        beholdning(token).andExpect { jsonPath("$.varer.length()") { value(0) } }
    }

    @Test
    fun `ny bruker har tom beholdning uten skanning`() {
        beholdning(registrer()).andExpect {
            status { isOk() }
            jsonPath("$.varer.length()") { value(0) }
            jsonPath("$.skanning") { value(null) }
        }
    }

    @Test
    fun `andre kan ikke lagre til din skanning, og ser ikke din beholdning`() {
        val eier = registrer()
        val id = skann(eier)
        lagre(eier, id, """[{"navn":"melk","antall":1}]""")
        val fremmed = registrer()

        lagre(fremmed, id, """[{"navn":"sjokolade","antall":9}]""").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ikke_funnet") }
        }
        beholdning(fremmed).andExpect { jsonPath("$.varer.length()") { value(0) } }
        beholdning(eier).andExpect { jsonPath("$.varer[0].navn") { value("melk") } }
    }

    @Test
    fun `ukjent skanning gir 404`() {
        lagre(registrer(), 999_999_999, "[]").andExpect { status { isNotFound() } }
    }

    @Test
    fun `AI-indeks utenfor AI-ens liste avvises`() {
        val token = registrer()
        lagre(token, skann(token), """[{"navn":"melk","antall":1,"aiIndeks":${FakeVisionProvider.VARER.size}}]""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("ugyldig_foresporsel") }
            }
    }

    @Test
    fun `ugyldig antall og tomt navn avvises med norsk melding`() {
        val token = registrer()
        val id = skann(token)

        lagre(token, id, """[{"navn":"melk","antall":0}]""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Antall må være minst 1.") }
        }
        lagre(token, id, """[{"navn":"  ","antall":1}]""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("En vare mangler navn.") }
        }
    }

    @Test
    fun `eieren får bildet fra skanningen, andre får 404`() {
        val eier = registrer()
        val id = skann(eier)

        mvc.get("/api/skanninger/$id/bilde") { header("Authorization", "Bearer $eier") }.andExpect {
            status { isOk() }
            content { contentType("image/jpeg") }
            content { bytes(jpeg) }
            header { string("Cache-Control", org.hamcrest.Matchers.containsString("private")) }
        }
        mvc.get("/api/skanninger/$id/bilde") { header("Authorization", "Bearer ${registrer()}") }
            .andExpect { status { isNotFound() } }
        mvc.get("/api/skanninger/$id/bilde").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `lagring og beholdning krever innlogging`() {
        mvc.post("/api/skanninger/1/beholdning") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"varer":[]}"""
        }.andExpect { status { isUnauthorized() } }
        mvc.get("/api/beholdning").andExpect { status { isUnauthorized() } }
    }
}
