package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.AppTest
import com.haakenstad.fridgecam.analyse.AiLeverandor
import com.haakenstad.fridgecam.analyse.AiValg
import com.haakenstad.fridgecam.husstand.HusstandRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

/** `/api/husstand/ai` mot hele appen (fake-leverandør): valg av leverandør/modell og nøkler. */
class AiInnstillingerTest : AppTest() {

    @Autowired private lateinit var jdbc: NamedParameterJdbcTemplate
    @Autowired private lateinit var kryptering: NokkelKryptering
    @Autowired private lateinit var husstander: HusstandRepository

    private val nokkel = "sk-ant-api03-testnokkel-som-ikke-er-ekte-WXYZ"

    private fun hent(token: String) = mvc.get("/api/husstand/ai") { header("Authorization", "Bearer $token") }

    private fun lagreNokkel(token: String, verdi: String, leverandor: String = "anthropic"): ResultActionsDsl =
        mvc.put("/api/husstand/ai/nokkel") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"leverandor":"$leverandor","nokkel":"$verdi"}"""
        }

    private fun fjernNokkel(token: String, leverandor: String = "anthropic") =
        mvc.delete("/api/husstand/ai/nokkel/$leverandor") { header("Authorization", "Bearer $token") }

    private fun velg(token: String, leverandor: String, modell: String): ResultActionsDsl =
        mvc.put("/api/husstand/ai/valg") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"leverandor":"$leverandor","modell":"$modell"}"""
        }

    private fun husstandId(token: String): Long =
        Regex("\"husstander\":\\[\\{\"id\":(\\d+)").find(hent("/api/meg", token).body!!)!!.groupValues[1].toLong()

    @Test
    fun `ny husstand bruker standardvalget og har ingen nøkler`() {
        hent(registrer()).andExpect {
            status { isOk() }
            jsonPath("$.kreves") { value(false) }
            jsonPath("$.valg.leverandor") { value("anthropic") }
            jsonPath("$.valg.modell") { value("claude-opus-5-5") }
            jsonPath("$.valg.egetValg") { value(false) }
            jsonPath("$.leverandorer[0].id") { value("anthropic") }
            jsonPath("$.leverandorer[0].nokkel") { value(null) }
            jsonPath("$.leverandorer[0].reserve") { value(false) }
            jsonPath("$.leverandorer[0].modeller[0].id") { value("claude-opus-5-5") }
            jsonPath("$.leverandorer[0].modeller[0].prisPerBildeUsd") { isNumber() }
            jsonPath("$.leverandorer[1].id") { value("gemini") }
        }
    }

    @Test
    fun `husstanden kan velge leverandør og modell fra listen`() {
        val token = registrer()

        velg(token, "gemini", "gemini-3.8-flash").andExpect {
            status { isOk() }
            jsonPath("$.valg.leverandor") { value("gemini") }
            jsonPath("$.valg.modell") { value("gemini-3.8-flash") }
            jsonPath("$.valg.egetValg") { value(true) }
        }
        assertThat(husstander.aiValg(husstandId(token))).isEqualTo(AiValg(AiLeverandor.GEMINI, "gemini-3.8-flash"))
    }

    @Test
    fun `modeller utenfor listen og ukjente leverandører avvises`() {
        val token = registrer()

        velg(token, "anthropic", "claude-finnes-ikke").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Ukjent modell.") }
        }
        velg(token, "gemini", "claude-opus-5-5").andExpect { status { isBadRequest() } }
        velg(token, "fake", "fake").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Ukjent AI-leverandør.") }
        }
        hent(token).andExpect { jsonPath("$.valg.egetValg") { value(false) } }
    }

    @Test
    fun `lagret nøkkel vises bare med de fire siste tegnene, per leverandør`() {
        val token = registrer()

        val svar = lagreNokkel(token, nokkel).andExpect {
            status { isOk() }
            jsonPath("$.leverandorer[0].nokkel.sisteTegn") { value("WXYZ") }
            jsonPath("$.leverandorer[0].nokkel.endretAv") { value("Test") }
            jsonPath("$.leverandorer[1].nokkel") { value(null) }
        }.andReturn().response.contentAsString
        lagreNokkel(token, "AIzaSy-gemini-testnokkel-GGGG", "gemini")
            .andExpect { jsonPath("$.leverandorer[1].nokkel.sisteTegn") { value("GGGG") } }

        assertThat(svar).doesNotContain(nokkel).doesNotContain("sk-ant")
        assertThat(hent(token).andReturn().response.contentAsString).doesNotContain(nokkel).doesNotContain("AIzaSy")
    }

    @Test
    fun `nøkkelen ligger bare kryptert i databasen, knyttet til husstanden`() {
        val token = registrer()
        lagreNokkel(token, nokkel)
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
    fun `ny nøkkel erstatter den gamle, og fjerning sletter bare den leverandørens`() {
        val token = registrer()
        lagreNokkel(token, nokkel)
        lagreNokkel(token, "AIzaSy-gemini-testnokkel-GGGG", "gemini")

        lagreNokkel(token, "sk-ant-api03-en-annen-testnokkel-ABCD")
            .andExpect { jsonPath("$.leverandorer[0].nokkel.sisteTegn") { value("ABCD") } }
        fjernNokkel(token).andExpect { status { isNoContent() } }

        hent(token).andExpect {
            jsonPath("$.leverandorer[0].nokkel") { value(null) }
            jsonPath("$.leverandorer[1].nokkel.sisteTegn") { value("GGGG") }
        }
    }

    @Test
    fun `andre husstander ser ikke nøkkelen eller valget, og kan ikke endre dem`() {
        val eier = registrer()
        lagreNokkel(eier, nokkel)
        velg(eier, "anthropic", "claude-haiku-5-5")
        val fremmed = registrer()

        hent(fremmed).andExpect {
            jsonPath("$.leverandorer[0].nokkel") { value(null) }
            jsonPath("$.valg.egetValg") { value(false) }
        }
        fjernNokkel(fremmed)
        velg(fremmed, "gemini", "gemini-3.8-flash")
        hent(eier).andExpect {
            jsonPath("$.leverandorer[0].nokkel.sisteTegn") { value("WXYZ") }
            jsonPath("$.valg.modell") { value("claude-haiku-5-5") }
        }
    }

    @Test
    fun `ugyldig nøkkel avvises uten å bli gjentatt`() {
        val token = registrer()
        val admin = "sk-ant-admin01-testnokkel-som-ikke-er-ekte"

        val svar = lagreNokkel(token, admin).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ugyldig_foresporsel") }
        }.andReturn().response.contentAsString

        assertThat(svar).doesNotContain(admin)
        hent(token).andExpect { jsonPath("$.leverandorer[0].nokkel") { value(null) } }
    }

    @Test
    fun `krever innlogging`() {
        mvc.get("/api/husstand/ai").andExpect { status { isUnauthorized() } }
        mvc.put("/api/husstand/ai/valg") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"leverandor":"gemini","modell":"gemini-3.8-flash"}"""
        }.andExpect { status { isUnauthorized() } }
        mvc.put("/api/husstand/ai/nokkel") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"leverandor":"anthropic","nokkel":"$nokkel"}"""
        }.andExpect { status { isUnauthorized() } }
        mvc.delete("/api/husstand/ai/nokkel/anthropic").andExpect { status { isUnauthorized() } }
    }
}
