package com.haakenstad.fridgecam.analyse

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AnalysePromptTest {

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `tolker gyldig svar`() {
        val varer = AnalysePrompt.tolk(
            """{"varer":[{"navn":"melk","antall":2,"sikkerhet":"høy"},{"navn":"agurk","antall":1,"sikkerhet":"lav"}]}""",
            mapper,
        )

        assertThat(varer).containsExactly(
            FunnetVare("melk", 2, Sikkerhet.HOY),
            FunnetVare("agurk", 1, Sikkerhet.LAV),
        )
    }

    @Test
    fun `rydder i navn og antall`() {
        val varer = AnalysePrompt.tolk(
            """{"varer":[
                {"navn":"  Lettmelk ","antall":0,"sikkerhet":"høy"},
                {"navn":"egg","antall":500,"sikkerhet":"middels"},
                {"navn":"ost","sikkerhet":"høy"}
            ]}""",
            mapper,
        )

        assertThat(varer).containsExactly(
            FunnetVare("lettmelk", 1, Sikkerhet.HOY),
            FunnetVare("egg", AnalysePrompt.MAKS_ANTALL, Sikkerhet.MIDDELS),
            FunnetVare("ost", 1, Sikkerhet.HOY),
        )
    }

    @Test
    fun `hopper over ugyldige oppføringer og beholder resten`() {
        val varer = AnalysePrompt.tolk(
            """{"varer":[{"navn":"","antall":1,"sikkerhet":"høy"},{"antall":1},{"navn":42},{"navn":"smør","antall":1,"sikkerhet":"høy"}]}""",
            mapper,
        )

        assertThat(varer).containsExactly(FunnetVare("smør", 1, Sikkerhet.HOY))
    }

    @Test
    fun `ukjent sikkerhet blir lav`() {
        val varer = AnalysePrompt.tolk("""{"varer":[{"navn":"juice","antall":1,"sikkerhet":"kanskje"}]}""", mapper)

        assertThat(varer.single().sikkerhet).isEqualTo(Sikkerhet.LAV)
    }

    @Test
    fun `tom liste er gyldig`() {
        assertThat(AnalysePrompt.tolk("""{"varer":[]}""", mapper)).isEmpty()
    }

    @Test
    fun `avviser svar som ikke er JSON eller mangler varer`() {
        assertThatThrownBy { AnalysePrompt.tolk("Her er varene: melk", mapper) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { AnalysePrompt.tolk("""{"items":[]}""", mapper) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
