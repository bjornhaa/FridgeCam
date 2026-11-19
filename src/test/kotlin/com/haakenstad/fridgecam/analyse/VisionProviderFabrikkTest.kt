package com.haakenstad.fridgecam.analyse

import com.haakenstad.fridgecam.ainokkel.AiNokkel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class VisionProviderFabrikkTest {

    private val mapper = JsonMapper.builder().build()

    private fun fabrikk(props: AiProperties) = VisionProviderFabrikk(props, mapper)

    @Test
    fun `fake gir FakeVisionProvider uten nøkkel`() {
        val fabrikk = fabrikk(AiProperties(provider = AiLeverandor.FAKE))

        assertThat(fabrikk.kreverNokkel).isFalse()
        assertThat(fabrikk.nokkelLeverandor).isEqualTo(AiLeverandor.ANTHROPIC)
        fabrikk.lag(null).use {
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
    fun `anthropic krever husstandens nøkkel`() {
        val fabrikk = fabrikk(AiProperties(provider = AiLeverandor.ANTHROPIC))

        assertThat(fabrikk.kreverNokkel).isTrue()
        assertThatThrownBy { fabrikk.lag(null) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `anthropic bruker standardmodell når AI_MODEL er tom`() {
        fabrikk(AiProperties(provider = AiLeverandor.ANTHROPIC, model = "")).lag(AiNokkel("sk-ant-test")).use {
            assertThat(it.navn).isEqualTo("anthropic/claude-opus-5-5 (effort medium)")
        }
    }

    @Test
    fun `gemini bruker standardmodell når AI_MODEL er tom`() {
        val fabrikk = fabrikk(AiProperties(provider = AiLeverandor.GEMINI, model = " "))

        assertThat(fabrikk.nokkelLeverandor).isEqualTo(AiLeverandor.GEMINI)
        fabrikk.lag(AiNokkel("test")).use {
            assertThat(it.navn).isEqualTo("gemini/gemini-4-hydrogen (thinking medium)")
        }
    }

    @Test
    fun `AiProperties viser ikke nøklene i toString`() {
        val props = AiProperties(provider = AiLeverandor.ANTHROPIC, anthropicApiKey = "sk-ant-hemmelig", geminiApiKey = "AIza-hemmelig")

        assertThat(props.toString()).doesNotContain("hemmelig")
    }
}
