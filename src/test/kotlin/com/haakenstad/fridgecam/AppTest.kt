package com.haakenstad.fridgecam

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestClient
import java.util.UUID

/**
 * Felles oppsett for tester som starter hele appen med innebygd Postgres
 * (local-profilen). Klassene som arver deler samme Spring-kontekst og database,
 * så hver test bruker egne, unike e-postadresser.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        // Tilfeldig DB-port så testen ikke kolliderer med en lokalt kjørende app.
        "fridgecam.local-db.port=0",
        // Uavhengig av AI_PROVIDER i en lokal .env (local-profilen leser den).
        "fridgecam.ai.provider=fake",
        "fridgecam.ai.fake-forsinkelse=0s",
    ],
)
@ActiveProfiles("local")
@AutoConfigureMockMvc
abstract class AppTest {

    /** For tester som vil bruke MockMvc mot hele appen (med ekte sikkerhet og database). */
    @Autowired
    protected lateinit var mvc: MockMvc

    @LocalServerPort
    protected var port: Int = 0

    protected fun klient(): RestClient = RestClient.create("http://localhost:$port")

    /** Svaret som tekst, uten unntak på 4xx/5xx: testene sjekker statuskoden selv. */
    protected fun RestClient.RequestHeadersSpec<*>.svar(): ResponseEntity<String> =
        retrieve().onStatus({ true }) { _, _ -> }.toEntity(String::class.java)

    protected fun hent(sti: String, token: String? = null): ResponseEntity<String> =
        klient().get().uri(sti)
            .headers { h -> token?.let { h.setBearerAuth(it) } }
            .svar()

    protected fun postJson(sti: String, json: String): ResponseEntity<String> =
        klient().post().uri(sti)
            .header("Content-Type", "application/json")
            .body(json)
            .svar()

    protected fun unikEpost(): String = "test-${UUID.randomUUID()}@example.com"

    /** Registrerer en ny bruker og returnerer tokenet. */
    protected fun registrer(epost: String = unikEpost(), passord: String = "hemmelig123"): String {
        val respons = postJson(
            "/api/auth/registrer",
            """{"epost":"$epost","navn":"Test","passord":"$passord"}""",
        )
        check(respons.statusCode.value() == 200) { "Registrering feilet: ${respons.body}" }
        return Regex("\"token\":\"([^\"]+)\"").find(respons.body!!)!!.groupValues[1]
    }
}
