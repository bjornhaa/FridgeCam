package com.haakenstad.fridgecam.analyse

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AiConfigTest {

    private val config = AiConfig()

    @Test
    fun `fake gir FakeVisionProvider`() {
        val provider = config.visionProvider(AiProperties(provider = AiLeverandor.FAKE))

        assertThat(provider).isInstanceOf(FakeVisionProvider::class.java)
        assertThat(provider.navn).isEqualTo("fake")
    }

    @Test
    fun `manglende AI_PROVIDER stopper oppstart med tydelig melding`() {
        assertThatThrownBy { config.visionProvider(AiProperties(provider = null)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("AI_PROVIDER er ikke satt")
            .hasMessageContaining("fake, anthropic, gemini")
    }

    @Test
    fun `leverandører som ikke er laget ennå gir tydelig melding`() {
        assertThatThrownBy { config.visionProvider(AiProperties(provider = AiLeverandor.ANTHROPIC)) }
            .hasMessageContaining("#9")
        assertThatThrownBy { config.visionProvider(AiProperties(provider = AiLeverandor.GEMINI)) }
            .hasMessageContaining("#10")
    }
}
