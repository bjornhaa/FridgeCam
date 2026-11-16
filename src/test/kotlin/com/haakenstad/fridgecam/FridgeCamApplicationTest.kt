package com.haakenstad.fridgecam

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient

/**
 * Starter hele appen med innebygd Postgres (local-profilen) og sjekker at
 * health-endepunktet svarer UP. Dekker samtidig at Flyway-migreringene
 * lar seg kjøre ved oppstart.
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
        val respons = RestClient.create("http://localhost:$port")
            .get().uri("/actuator/health")
            .retrieve()
            .toEntity(String::class.java)

        assertThat(respons.statusCode.value()).isEqualTo(200)
        assertThat(respons.body).contains("\"status\":\"UP\"")
    }
}
