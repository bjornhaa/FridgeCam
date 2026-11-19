package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.AppTest
import com.haakenstad.fridgecam.analyse.AiLeverandor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

/** `/api/husstand/ai-nokkel` mot hele appen (fake-leverandør, nøkkelen lagres som Anthropic-nøkkel). */
class AiNokkelTest : AppTest() {

    @Autowired private lateinit var jdbc: NamedParameterJdbcTemplate
    @Autowired private lateinit var kryptering: NokkelKryptering

    private val nokkel = "sk-ant-api03-testnokkel-som-ikke-er-ekte-WXYZ"

    private fun hentStatus(token: String) = mvc.get("/api/husstand/ai-nokkel") { header("Authorization", "Bearer $token") }

    private fun lagre(token: String, verdi: String): ResultActionsDsl =
        mvc.put("/api/husstand/ai-nokkel") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nokkel":"$verdi"}"""
        }

    private fun husstandId(token: String): Long =
        Regex("\"husstander\":\\[\\{\"id\":(\\d+)").find(hent("/api/meg", token).body!!)!!.groupValues[1].toLong()

    @Test
    fun `ny husstand har ingen nøkkel`() {
        hentStatus(registrer()).andExpect {
            status { isOk() }
            jsonPath("$.leverandor") { value("anthropic") }
            jsonPath("$.kreves") { value(false) }
            jsonPath("$.nokkel") { value(null) }
            jsonPath("$.reserve") { value(false) }
        }
    }

    @Test
    fun `lagret nøkkel vises bare med de fire siste tegnene`() {
        val token = registrer()

        val svar = lagre(token, nokkel).andExpect {
            status { isOk() }
            jsonPath("$.nokkel.sisteTegn") { value("WXYZ") }
            jsonPath("$.nokkel.endretAv") { value("Test") }
            jsonPath("$.nokkel.endret") { exists() }
        }.andReturn().response.contentAsString
        val status = hentStatus(token).andReturn().response.contentAsString

        assertThat(svar).doesNotContain(nokkel).doesNotContain("sk-ant")
        assertThat(status).doesNotContain(nokkel).contains("WXYZ")
    }

    @Test
    fun `nøkkelen ligger bare kryptert i databasen, knyttet til husstanden`() {
        val token = registrer()
        lagre(token, nokkel)
        val husstand = husstandId(token)

        val rad = jdbc.queryForMap(
            "SELECT nokkel_kryptert, siste_tegn, leverandor FROM husstand_ai_nokkel WHERE husstand_id = :h",
            mapOf("h" to husstand),
        )
        val kryptert = rad["nokkel_kryptert"] as String
        assertThat(kryptert).doesNotContain(nokkel).doesNotContain("testnokkel")
        assertThat(rad["siste_tegn"]).isEqualTo("WXYZ")
        assertThat(rad["leverandor"]).isEqualTo("ANTHROPIC")
        assertThat(kryptering.dekrypter(kryptert, husstand, AiLeverandor.ANTHROPIC)).isEqualTo(nokkel)
    }

    @Test
    fun `ny nøkkel erstatter den gamle, og fjerning sletter den`() {
        val token = registrer()
        lagre(token, nokkel)

        lagre(token, "sk-ant-api03-en-annen-testnokkel-ABCD").andExpect { jsonPath("$.nokkel.sisteTegn") { value("ABCD") } }
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM husstand_ai_nokkel WHERE husstand_id = :h", mapOf("h" to husstandId(token)), Int::class.java,
        )).isEqualTo(1)

        mvc.delete("/api/husstand/ai-nokkel") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNoContent() } }
        hentStatus(token).andExpect { jsonPath("$.nokkel") { value(null) } }
    }

    @Test
    fun `andre husstander ser ikke nøkkelen og kan ikke fjerne den`() {
        val eier = registrer()
        lagre(eier, nokkel)
        val fremmed = registrer()

        hentStatus(fremmed).andExpect { jsonPath("$.nokkel") { value(null) } }
        mvc.delete("/api/husstand/ai-nokkel") { header("Authorization", "Bearer $fremmed") }
        hentStatus(eier).andExpect { jsonPath("$.nokkel.sisteTegn") { value("WXYZ") } }
    }

    @Test
    fun `ugyldig nøkkel avvises uten å bli gjentatt`() {
        val token = registrer()
        val admin = "sk-ant-admin01-testnokkel-som-ikke-er-ekte"

        val svar = lagre(token, admin).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ugyldig_foresporsel") }
        }.andReturn().response.contentAsString

        assertThat(svar).doesNotContain(admin)
        hentStatus(token).andExpect { jsonPath("$.nokkel") { value(null) } }
    }

    @Test
    fun `krever innlogging`() {
        mvc.get("/api/husstand/ai-nokkel").andExpect { status { isUnauthorized() } }
        mvc.put("/api/husstand/ai-nokkel") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nokkel":"$nokkel"}"""
        }.andExpect { status { isUnauthorized() } }
        mvc.delete("/api/husstand/ai-nokkel").andExpect { status { isUnauthorized() } }
    }
}
