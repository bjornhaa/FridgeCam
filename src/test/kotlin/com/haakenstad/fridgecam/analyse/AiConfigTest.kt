package com.haakenstad.fridgecam.analyse

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AiConfigTest {

    private val config = AiConfig()
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `fake gir FakeVisionProvider`() {
        val provider = config.visionProvider(AiProperties(provider = AiLeverandor.FAKE), mapper)

        assertThat(provider).isInstanceOf(FakeVisionProvider::class.java)
        assertThat(provider.navn).isEqualTo("fake")
    }

    @Test
    fun `manglende AI_PROVIDER stopper oppstart med tydelig melding`() {
        assertThatThrownBy { config.visionProvider(AiProperties(provider = null), mapper) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("AI_PROVIDER er ikke satt")
            .hasMessageContaining("fake, anthropic, gemini")
    }

    @Test
    fun `anthropic uten API-nøkkel stopper oppstart`() {
        assertThatThrownBy { config.visionProvider(AiProperties(provider = AiLeverandor.ANTHROPIC, anthropicApiKey = " "), mapper) }
            .hasMessageContaining("ANTHROPIC_API_KEY")
    }

    @Test
    fun `anthropic bruker standardmodell når AI_MODEL er tom`() {
        val provider = config.visionProvider(
            AiProperties(provider = AiLeverandor.ANTHROPIC, anthropicApiKey = "sk-ant-test", model = ""),
            mapper,
        )

        assertThat(provider.navn).isEqualTo("anthropic/claude-opus-5-5")
        (provider as AutoCloseable).close()
    }

    @Test
    fun `gemini gir tydelig melding om at den ikke er laget ennå`() {
        assertThatThrownBy { config.visionProvider(AiProperties(provider = AiLeverandor.GEMINI), mapper) }
            .hasMessageContaining("#10")
    }
}
