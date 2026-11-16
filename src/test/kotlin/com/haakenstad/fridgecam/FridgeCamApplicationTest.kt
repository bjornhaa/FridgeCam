package com.haakenstad.fridgecam

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap

/**
 * Starter hele appen med innebygd Postgres. Dekker samtidig at
 * Flyway-migreringene lar seg kjøre ved oppstart.
 *
 * `src/test/resources/static/index.html` står i for den bygde frontenden,
 * så testene ikke trenger Node.
 */
class FridgeCamApplicationTest : AppTest() {

    @Test
    fun `health svarer UP uten innlogging`() {
        val respons = hent("/actuator/health")

        assertThat(respons.statusCode.value()).isEqualTo(200)
        assertThat(respons.body).contains("\"status\":\"UP\"")
    }

    @Test
    fun `forsiden serverer frontenden uten innlogging`() {
        val respons = hent("/")

        assertThat(respons.statusCode.value()).isEqualTo(200)
        assertThat(respons.body).contains("fridgecam-test-index")
    }

    @Test
    fun `dyp lenke i appen gir index_html`() {
        val respons = hent("/handleliste/123")

        assertThat(respons.statusCode.value()).isEqualTo(200)
        assertThat(respons.body).contains("fridgecam-test-index")
    }

    @Test
    fun `ukjent API-sti gir 404 som JSON, ikke index_html`() {
        val respons = hent("/api/finnes-ikke", registrer())

        assertThat(respons.statusCode.value()).isEqualTo(404)
        assertThat(respons.body).doesNotContain("fridgecam-test-index")
    }

    @Test
    fun `analyse fungerer gjennom hele appen`() {
        val respons = sendBilde(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()), registrer())

        assertThat(respons.statusCode.value()).isEqualTo(200)
        assertThat(respons.body).contains("\"leverandor\":\"fake\"").contains("\"navn\":\"melk\"")
    }

    @Test
    fun `analyse uten innlogging avvises`() {
        val respons = sendBilde(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()), token = null)

        assertThat(respons.statusCode.value()).isEqualTo(401)
        assertThat(respons.body).contains("\"code\":\"ikke_innlogget\"")
    }

    @Test
    fun `for stort bilde gir 413 med feilkode`() {
        val respons = sendBilde(ByteArray(11 * 1024 * 1024), registrer())

        assertThat(respons.statusCode.value()).isEqualTo(413)
        assertThat(respons.body).contains("bilde_for_stort")
    }

    private fun sendBilde(data: ByteArray, token: String?): ResponseEntity<String> {
        val skjema = LinkedMultiValueMap<String, Any>()
        skjema.add("bilde", object : ByteArrayResource(data) {
            override fun getFilename() = "kjoleskap.jpg"
        })
        return klient().post().uri("/api/analyze")
            .headers { h -> token?.let { h.setBearerAuth(it) } }
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(skjema)
            .svar()
    }
}
