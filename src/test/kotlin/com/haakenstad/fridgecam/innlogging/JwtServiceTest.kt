package com.haakenstad.fridgecam.innlogging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

class JwtServiceTest {

    private val hemmelighet = "en-hemmelighet-som-er-lang-nok-for-hs256"
    private val jwt = JwtService(JwtEgenskaper(hemmelighet))

    @Test
    fun `token gir tilbake bruker-id-en`() {
        assertThat(jwt.lesBrukerId(jwt.lag(42))).isEqualTo(42)
    }

    @Test
    fun `utløpt token godtas ikke`() {
        val utlopt = JwtService(JwtEgenskaper(hemmelighet, levetid = Duration.ofSeconds(-1))).lag(42)

        assertThat(jwt.lesBrukerId(utlopt)).isNull()
    }

    @Test
    fun `token signert med en annen hemmelighet godtas ikke`() {
        val fremmed = JwtService(JwtEgenskaper("en-helt-annen-hemmelighet-som-ogsa-er-lang")).lag(42)

        assertThat(jwt.lesBrukerId(fremmed)).isNull()
    }

    @Test
    fun `endret token godtas ikke`() {
        val (hode, innhold, signatur) = jwt.lag(42).split(".")
        val endret = "$hode.${innhold.reversed()}.$signatur"

        assertThat(jwt.lesBrukerId(endret)).isNull()
    }

    @Test
    fun `søppel og tomt token godtas ikke`() {
        assertThat(jwt.lesBrukerId("ikke.et.token")).isNull()
        assertThat(jwt.lesBrukerId("")).isNull()
    }

    @Test
    fun `appen starter ikke uten hemmelighet`() {
        assertThatThrownBy { JwtService(JwtEgenskaper("")) }
            .hasMessageContaining("JWT_SECRET er ikke satt")
    }

    @Test
    fun `appen starter ikke med for kort hemmelighet`() {
        assertThatThrownBy { JwtService(JwtEgenskaper("kort")) }
            .hasMessageContaining("for kort")
    }
}
