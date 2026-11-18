package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.analyse.AiLeverandor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.util.Base64

class NokkelKrypteringTest {

    private val hovednokkel = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
    private val kryptering = NokkelKryptering(hovednokkel, MockEnvironment())
    private val nokkel = "sk-ant-api03-dette-er-en-testnokkel-1234"

    @Test
    fun `kryptert verdi kan dekrypteres igjen og inneholder ikke klarteksten`() {
        val kryptert = kryptering.krypter(nokkel, 1, AiLeverandor.ANTHROPIC)

        assertThat(kryptert).startsWith("v1:").doesNotContain(nokkel).doesNotContain("sk-ant")
        assertThat(kryptering.dekrypter(kryptert, 1, AiLeverandor.ANTHROPIC)).isEqualTo(nokkel)
    }

    @Test
    fun `samme nøkkel gir forskjellig chiffertekst hver gang`() {
        assertThat(kryptering.krypter(nokkel, 1, AiLeverandor.ANTHROPIC))
            .isNotEqualTo(kryptering.krypter(nokkel, 1, AiLeverandor.ANTHROPIC))
    }

    @Test
    fun `kryptert verdi kan ikke brukes for en annen husstand eller leverandør`() {
        val kryptert = kryptering.krypter(nokkel, 1, AiLeverandor.ANTHROPIC)

        assertThatThrownBy { kryptering.dekrypter(kryptert, 2, AiLeverandor.ANTHROPIC) }
        assertThatThrownBy { kryptering.dekrypter(kryptert, 1, AiLeverandor.GEMINI) }
    }

    @Test
    fun `endret chiffertekst oppdages`() {
        val kryptert = kryptering.krypter(nokkel, 1, AiLeverandor.ANTHROPIC)
        val data = Base64.getDecoder().decode(kryptert.removePrefix("v1:"))
        data[20] = (data[20].toInt() xor 1).toByte()

        assertThatThrownBy { kryptering.dekrypter("v1:" + Base64.getEncoder().encodeToString(data), 1, AiLeverandor.ANTHROPIC) }
    }

    @Test
    fun `annen hovednøkkel kan ikke dekryptere`() {
        val kryptert = kryptering.krypter(nokkel, 1, AiLeverandor.ANTHROPIC)
        val annen = NokkelKryptering(Base64.getEncoder().encodeToString(ByteArray(32) { 7 }), MockEnvironment())

        assertThatThrownBy { annen.dekrypter(kryptert, 1, AiLeverandor.ANTHROPIC) }
    }

    @Test
    fun `manglende eller ugyldig hovednøkkel stopper oppstart uten å vise verdien`() {
        assertThatThrownBy { NokkelKryptering("", MockEnvironment()) }
            .hasMessageContaining("FRIDGECAM_NOKKEL_KRYPTERING må settes")
        assertThatThrownBy { NokkelKryptering("ikke-base64!!", MockEnvironment()) }
            .hasMessageContaining("ikke gyldig base64")
            .hasMessageNotContaining("ikke-base64!!")
        assertThatThrownBy { NokkelKryptering(Base64.getEncoder().encodeToString(ByteArray(16) { 1 }), MockEnvironment()) }
            .hasMessageContaining("32 byte")
    }

    @Test
    fun `utviklingsnøkkelen godtas bare med local-profilen`() {
        val utvikling = Base64.getEncoder().encodeToString(ByteArray(32))

        assertThatThrownBy { NokkelKryptering(utvikling, MockEnvironment()) }
            .hasMessageContaining("utviklingsnøkkelen")
        NokkelKryptering(utvikling, MockEnvironment().apply { setActiveProfiles("local") })
    }
}
