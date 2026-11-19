package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.ainokkel.AiNokkelService.Companion.reserveNokkel
import com.haakenstad.fridgecam.ainokkel.AiNokkelService.Companion.validerNokkel
import com.haakenstad.fridgecam.analyse.AiLeverandor
import com.haakenstad.fridgecam.analyse.AiProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AiNokkelValideringTest {

    private val gyldig = "sk-ant-api03-abcdefghijklmnopqrstuvwxyz"

    @Test
    fun `gyldig nøkkel godtas og trimmes`() {
        assertThat(validerNokkel(AiLeverandor.ANTHROPIC, "  $gyldig\n")).isEqualTo(gyldig)
        assertThat(validerNokkel(AiLeverandor.GEMINI, "AIzaSyabcdefghijklmnopqrstuvwxyz")).isEqualTo("AIzaSyabcdefghijklmnopqrstuvwxyz")
    }

    @Test
    fun `feil form avvises uten at nøkkelen gjentas i meldingen`() {
        val tilfeller = mapOf(
            "" to "Lim inn nøkkelen.",
            "sk-ant-api03-abc def-ghijklmnopqrstu" to "uten mellomrom",
            "sk-ant-admin01-abcdefghijklmnopqrstuvwxyz" to "admin-nøkkel",
            "AIzaSyabcdefghijklmnopqrstuvwxyz" to "begynner med «sk-ant-»",
            "sk-ant-kort" to "for kort",
            "sk-ant-" + "x".repeat(600) to "for lang",
        )
        for ((nokkel, melding) in tilfeller) {
            assertThatThrownBy { validerNokkel(AiLeverandor.ANTHROPIC, nokkel) }
                .`as`(nokkel.take(30))
                .hasMessageContaining(melding)
                .satisfies({ e -> if (nokkel.isNotEmpty()) assertThat(e.message).doesNotContain(nokkel) })
                .extracting("code").isEqualTo("ugyldig_foresporsel")
        }
    }

    @Test
    fun `reserve fra miljøet brukes bare lokalt`() {
        val props = AiProperties(anthropicApiKey = gyldig, geminiApiKey = " ")

        assertThat(reserveNokkel(props, AiLeverandor.ANTHROPIC, lokal = true)?.verdi).isEqualTo(gyldig)
        assertThat(reserveNokkel(props, AiLeverandor.ANTHROPIC, lokal = false)).isNull()
        assertThat(reserveNokkel(props, AiLeverandor.GEMINI, lokal = true)).isNull()
    }

    @Test
    fun `AiNokkel og forespørselen viser ikke nøkkelen i toString`() {
        assertThat(AiNokkel(gyldig).toString()).doesNotContain(gyldig)
        assertThat(AiNokkelForesporsel("anthropic", gyldig).toString()).doesNotContain(gyldig)
    }
}
