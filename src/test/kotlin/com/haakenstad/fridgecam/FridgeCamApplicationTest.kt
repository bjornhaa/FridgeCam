package com.haakenstad.fridgecam

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient

/**
 * Starter hele appen med innebygd Postgres (local-profilen). Dekker samtidig
 * at Flyway-migreringene lar seg kjøre ved oppstart.
 *
 * `src/test/resources/static/index.html` står i for den bygde frontenden,
 * så testene ikke trenger Node.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // Tilfeldig DB-port så testen ikke kolliderer med en lokalt kjørende app.
    properties = ["fridgecam.local-db.port=0"],
)
@ActiveProfiles("local")
class FridgeCamApplicationTest {

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `health svarer UP`() {
        val respons = hent("/actuator/health")

        assertThat(respons.statusCode.value()).isEqualTo(200)
        assertThat(respons.body).contains("\"status\":\"UP\"")
    }

    @Test
    fun `forsiden serverer frontenden`() {
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
        val respons = hent("/api/finnes-ikke")

        assertThat(respons.statusCode.value()).isEqualTo(404)
        assertThat(respons.body).doesNotContain("fridgecam-test-index")
    }

    private fun hent(sti: String): ResponseEntity<String> =
        RestClient.create("http://localhost:$port")
            .get().uri(sti)
            .retrieve()
            // Vi vil se statuskoden selv, ikke få unntak på 4xx.
            .onStatus({ true }) { _, _ -> }
            .toEntity(String::class.java)
}
