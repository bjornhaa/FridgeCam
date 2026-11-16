package com.haakenstad.fridgecam.innlogging

import com.haakenstad.fridgecam.AppTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/** Registrering, innlogging og `/api/meg` gjennom hele appen, mot ekte database. */
class InnloggingTest : AppTest() {

    @Autowired
    private lateinit var jdbc: NamedParameterJdbcTemplate

    @Test
    fun `registrering gir token, og meg viser brukeren som eier av en ny husstand`() {
        val epost = unikEpost()
        val token = registrer(epost)

        val meg = hent("/api/meg", token)

        assertThat(meg.statusCode.value()).isEqualTo(200)
        assertThat(meg.body)
            .contains("\"epost\":\"$epost\"")
            .contains("\"navn\":\"Test\"")
            .contains("\"rolle\":\"EIER\"")
            .contains("\"navn\":\"Hjemme\"")
            .doesNotContain("passord")
    }

    @Test
    fun `e-post lagres med små bokstaver og innlogging bryr seg ikke om store og små`() {
        val epost = unikEpost()
        registrer(epost.uppercase())

        val respons = loggInn(epost.replaceFirstChar { it.uppercase() }, "hemmelig123")

        assertThat(respons.statusCode.value()).isEqualTo(200)
        assertThat(respons.body).contains("\"token\":").contains("\"epost\":\"$epost\"")
    }

    @Test
    fun `token fra innlogging gir tilgang til meg`() {
        val epost = unikEpost()
        registrer(epost)
        val token = Regex("\"token\":\"([^\"]+)\"").find(loggInn(epost, "hemmelig123").body!!)!!.groupValues[1]

        assertThat(hent("/api/meg", token).statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `feil passord avvises`() {
        val epost = unikEpost()
        registrer(epost)

        val respons = loggInn(epost, "feilpassord")

        assertThat(respons.statusCode.value()).isEqualTo(401)
        assertThat(respons.body).contains("\"code\":\"ugyldig_innlogging\"")
    }

    @Test
    fun `ukjent e-post gir samme feil som feil passord`() {
        val respons = loggInn(unikEpost(), "hemmelig123")

        assertThat(respons.statusCode.value()).isEqualTo(401)
        assertThat(respons.body).contains("\"code\":\"ugyldig_innlogging\"")
    }

    @Test
    fun `duplikat e-post avvises uten å lage en ekstra husstand`() {
        val epost = unikEpost()
        registrer(epost)
        val husstanderFor = antallHusstander()

        val respons = postJson(
            "/api/auth/registrer",
            """{"epost":"${epost.uppercase()}","navn":"Igjen","passord":"hemmelig123","registreringskode":"$REGISTRERINGSKODE"}""",
        )

        assertThat(respons.statusCode.value()).isEqualTo(409)
        assertThat(respons.body).contains("\"code\":\"epost_i_bruk\"")
        assertThat(antallHusstander()).isEqualTo(husstanderFor)
    }

    @Test
    fun `feil registreringskode avvises`() {
        val respons = postJson(
            "/api/auth/registrer",
            """{"epost":"${unikEpost()}","navn":"Test","passord":"hemmelig123","registreringskode":"gjetning"}""",
        )

        assertThat(respons.statusCode.value()).isEqualTo(403)
        assertThat(respons.body).contains("\"code\":\"ugyldig_registreringskode\"")
    }

    @Test
    fun `manglende registreringskode avvises`() {
        val respons = postJson(
            "/api/auth/registrer",
            """{"epost":"${unikEpost()}","navn":"Test","passord":"hemmelig123"}""",
        )

        assertThat(respons.statusCode.value()).isEqualTo(403)
        assertThat(respons.body).contains("\"code\":\"ugyldig_registreringskode\"")
    }

    @Test
    fun `for kort passord avvises med norsk melding`() {
        val respons = postJson(
            "/api/auth/registrer",
            """{"epost":"${unikEpost()}","navn":"Test","passord":"kort","registreringskode":"$REGISTRERINGSKODE"}""",
        )

        assertThat(respons.statusCode.value()).isEqualTo(400)
        assertThat(respons.body).contains("\"code\":\"ugyldig_foresporsel\"").contains("mellom 8 og 72 tegn")
    }

    @Test
    fun `passord over 72 byte avvises selv om det er under 72 tegn`() {
        val respons = postJson(
            "/api/auth/registrer",
            """{"epost":"${unikEpost()}","navn":"Test","passord":"${"æ".repeat(40)}","registreringskode":"$REGISTRERINGSKODE"}""",
        )

        assertThat(respons.statusCode.value()).isEqualTo(400)
        assertThat(respons.body).contains("\"code\":\"ugyldig_foresporsel\"")
    }

    @Test
    fun `ugyldig JSON gir 400`() {
        val respons = postJson("/api/auth/logg-inn", "{ikke json")

        assertThat(respons.statusCode.value()).isEqualTo(400)
        assertThat(respons.body).contains("\"code\":\"ugyldig_foresporsel\"")
    }

    @Test
    fun `meg uten token avvises`() {
        val respons = hent("/api/meg")

        assertThat(respons.statusCode.value()).isEqualTo(401)
        assertThat(respons.body).contains("\"code\":\"ikke_innlogget\"")
    }

    @Test
    fun `meg med ugyldig token avvises`() {
        val respons = hent("/api/meg", "ikke.et.token")

        assertThat(respons.statusCode.value()).isEqualTo(401)
        assertThat(respons.body).contains("\"code\":\"ikke_innlogget\"")
    }

    private fun loggInn(epost: String, passord: String) =
        postJson("/api/auth/logg-inn", """{"epost":"$epost","passord":"$passord"}""")

    private fun antallHusstander(): Int =
        jdbc.queryForObject("SELECT count(*) FROM husstand", emptyMap<String, Any>(), Int::class.java)!!
}
