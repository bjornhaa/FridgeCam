package com.haakenstad.fridgecam.innlogging

import com.haakenstad.fridgecam.common.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test

class RegistreringskodeTest {

    @Test
    fun `riktig kode slipper gjennom, også med mellomrom rundt`() {
        val kode = Registreringskode(RegistreringEgenskaper("hemmelig"))

        assertThatCode { kode.sjekk("hemmelig") }.doesNotThrowAnyException()
        assertThatCode { kode.sjekk(" hemmelig ") }.doesNotThrowAnyException()
    }

    @Test
    fun `feil eller tom kode avvises`() {
        val kode = Registreringskode(RegistreringEgenskaper("hemmelig"))

        assertThat(feilkode { kode.sjekk("gjetning") }).isEqualTo("ugyldig_registreringskode")
        assertThat(feilkode { kode.sjekk("") }).isEqualTo("ugyldig_registreringskode")
    }

    @Test
    fun `registrering er stengt når koden ikke er satt`() {
        for (ikkeSatt in listOf(null, "", "  ")) {
            val kode = Registreringskode(RegistreringEgenskaper(ikkeSatt))

            assertThat(feilkode { kode.sjekk("") }).isEqualTo("registrering_stengt")
            assertThat(feilkode { kode.sjekk("hva som helst") }).isEqualTo("registrering_stengt")
        }
    }

    private fun feilkode(blokk: () -> Unit): String =
        catchThrowableOfType(ApiException::class.java) { blokk() }.code
}
