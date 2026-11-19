package com.haakenstad.fridgecam.analyse

import com.haakenstad.fridgecam.ainokkel.AiNokkel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class VisionProviderFabrikkTest {

    private val mapper = JsonMapper.builder().build()
    private val nokkel = AiNokkel("sk-ant-test")

    private fun fabrikk(props: AiProperties) = VisionProviderFabrikk(props, mapper)

    @Test
    fun `fake gir FakeVisionProvider uansett valg, uten nøkkel`() {
        val fabrikk = fabrikk(AiProperties(provider = AiLeverandor.FAKE))

        assertThat(fabrikk.erFake).isTrue()
        assertThat(fabrikk.standardValg).isEqualTo(AiValg(AiLeverandor.ANTHROPIC, "claude-opus-5-5"))
        fabrikk.lag(AiValg(AiLeverandor.GEMINI, "gemini-3.8-flash"), null).use {
            assertThat(it).isInstanceOf(FakeVisionProvider::class.java)
            assertThat(it.navn).isEqualTo("fake")
        }
    }

    @Test
    fun `manglende AI_PROVIDER stopper oppstart med tydelig melding`() {
        assertThatThrownBy { fabrikk(AiProperties(provider = null)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("AI_PROVIDER er ikke satt")
            .hasMessageContaining("fake, anthropic, gemini")
    }

    @Test
    fun `ekte leverandør krever husstandens nøkkel`() {
        val fabrikk = fabrikk(AiProperties(provider = AiLeverandor.ANTHROPIC))

        assertThat(fabrikk.erFake).isFalse()
        assertThatThrownBy { fabrikk.lag(fabrikk.standardValg, null) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `standardvalget følger AI_PROVIDER og AI_MODEL, med standardmodell når AI_MODEL er tom`() {
        assertThat(fabrikk(AiProperties(provider = AiLeverandor.ANTHROPIC, model = "")).standardValg)
            .isEqualTo(AiValg(AiLeverandor.ANTHROPIC, "claude-opus-5-5"))
        assertThat(fabrikk(AiProperties(provider = AiLeverandor.GEMINI, model = " ")).standardValg)
            .isEqualTo(AiValg(AiLeverandor.GEMINI, "gemini-4-hydrogen"))
        assertThat(fabrikk(AiProperties(provider = AiLeverandor.ANTHROPIC, model = " claude-haiku-5-5 ")).standardValg)
            .isEqualTo(AiValg(AiLeverandor.ANTHROPIC, "claude-haiku-5-5"))
    }

    @Test
    fun `husstandens valg bestemmer leverandør og modell`() {
        val fabrikk = fabrikk(AiProperties(provider = AiLeverandor.ANTHROPIC))

        fabrikk.lag(AiValg(AiLeverandor.ANTHROPIC, "claude-sonnet-5-5"), nokkel).use {
            assertThat(it.navn).isEqualTo("anthropic/claude-sonnet-5-5 (effort medium)")
        }
        fabrikk.lag(AiValg(AiLeverandor.GEMINI, "gemini-3.8-flash"), AiNokkel("AIza-test")).use {
            assertThat(it.navn).isEqualTo("gemini/gemini-3.8-flash (thinking medium)")
        }
    }

    @Test
    fun `modell fra en annen leverandør stopper oppstart med tydelig melding`() {
        assertThatThrownBy { fabrikk(AiProperties(provider = AiLeverandor.GEMINI, model = "claude-haiku-5-5")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("AI_MODEL=claude-haiku-5-5 passer ikke med AI_PROVIDER=gemini")
        assertThatThrownBy { fabrikk(AiProperties(provider = AiLeverandor.ANTHROPIC, model = "gemini-4-hydrogen")) }
            .hasMessageContaining("Anthropic-modeller begynner med «claude-»")
        // Fake ser bort fra modellen.
        fabrikk(AiProperties(provider = AiLeverandor.FAKE, model = "claude-haiku-5-5"))
    }

    @Test
    fun `alle modeller i listen har kjent pris og riktig prefiks`() {
        for (modell in AiModeller.ALLE) {
            assertThat(modell.prisPerBildeUsd).`as`(modell.id).isNotNull().isPositive()
            val prefiks = if (modell.leverandor == AiLeverandor.ANTHROPIC) "claude-" else "gemini-"
            assertThat(modell.id).startsWith(prefiks)
        }
        assertThat(AiModeller.finn(AiLeverandor.ANTHROPIC, "claude-opus-5-5")).isNotNull()
        assertThat(AiModeller.finn(AiLeverandor.GEMINI, "claude-opus-5-5")).isNull()
    }

    @Test
    fun `AiProperties viser ikke nøklene i toString`() {
        val props = AiProperties(provider = AiLeverandor.ANTHROPIC, anthropicApiKey = "sk-ant-hemmelig", geminiApiKey = "AIza-hemmelig")

        assertThat(props.toString()).doesNotContain("hemmelig")
    }
}
