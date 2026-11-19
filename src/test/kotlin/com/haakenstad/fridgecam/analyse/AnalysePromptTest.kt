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

    @Test
    fun `tolker bokser`() {
        val vare = AnalysePrompt.tolk(
            """{"varer":[{"navn":"brunost","antall":1,"sikkerhet":"høy","bokser":[{"x1":100,"y1":200,"x2":300,"y2":400}]}]}""",
            mapper,
        ).single()

        assertThat(vare.bokser).containsExactly(Boks(100, 200, 300, 400))
    }

    @Test
    fun `klemmer bokser inn i bildet og retter byttede hjørner`() {
        val bokser = AnalysePrompt.tolk(
            """{"varer":[{"navn":"melk","antall":2,"sikkerhet":"høy","bokser":[
                {"x1":-50,"y1":900,"x2":200,"y2":1200},
                {"x1":800,"y1":600,"x2":700,"y2":500}
            ]}]}""",
            mapper,
        ).single().bokser

        assertThat(bokser).containsExactly(Boks(0, 900, 200, 1000), Boks(700, 500, 800, 600))
    }

    @Test
    fun `dropper ugyldige bokser men beholder varen`() {
        val vare = AnalysePrompt.tolk(
            """{"varer":[{"navn":"ost","antall":1,"sikkerhet":"høy","bokser":[
                {"x1":100,"y1":100,"x2":100,"y2":300},
                {"x1":"a","y1":0,"x2":10,"y2":10},
                {"x1":10,"y1":10,"x2":20},
                {"x1":10,"y1":10,"x2":20,"y2":20}
            ]}]}""",
            mapper,
        ).single()

        assertThat(vare.navn).isEqualTo("ost")
        assertThat(vare.bokser).containsExactly(Boks(10, 10, 20, 20))
    }

    @Test
    fun `vare uten bokser får tom liste`() {
        val vare = AnalysePrompt.tolk("""{"varer":[{"navn":"juice","antall":1,"sikkerhet":"høy"}]}""", mapper).single()

        assertThat(vare.bokser).isEmpty()
    }

    @Test
    fun `begrenser antall bokser per vare`() {
        val mange = (1..40).joinToString(",") { """{"x1":0,"y1":0,"x2":$it,"y2":$it}""" }
        val vare = AnalysePrompt.tolk("""{"varer":[{"navn":"egg","antall":40,"sikkerhet":"høy","bokser":[$mange]}]}""", mapper).single()

        assertThat(vare.bokser).hasSize(AnalysePrompt.MAKS_BOKSER)
    }

    @Test
    fun `tolker bokser i box_2d-format`() {
        val bokser = AnalysePrompt.tolk(
            """{"varer":[{"navn":"egg","antall":6,"sikkerhet":"høy","bokser":[[300,320,420,620],[1,2,3]]}]}""",
            mapper,
        ).single().bokser

        // [ymin, xmin, ymax, xmax]; boksen med tre tall droppes
        assertThat(bokser).containsExactly(Boks(320, 300, 620, 420))
    }

    @Test
    fun `boksformatet styrer prompt og skjema`() {
        assertThat(AnalysePrompt.system(Boksformat.BOX_2D)).contains("box_2d")
        assertThat(AnalysePrompt.system(Boksformat.HJORNER)).contains("x1,y1").doesNotContain("box_2d")

        @Suppress("UNCHECKED_CAST")
        fun boksSkjema(format: Boksformat): Map<String, Any> {
            val varer = (AnalysePrompt.skjema(format)["properties"] as Map<String, Any>)["varer"] as Map<String, Any>
            val vare = (varer["items"] as Map<String, Any>)["properties"] as Map<String, Any>
            return (vare["bokser"] as Map<String, Any>)["items"] as Map<String, Any>
        }
        assertThat(boksSkjema(Boksformat.BOX_2D)["type"]).isEqualTo("array")
        assertThat(boksSkjema(Boksformat.HJORNER)["type"]).isEqualTo("object")
    }

    @Test
    fun `bokser i piksler regnes om til 0–1000 og klemmes inn i bildet`() {
        val json = """{"varer":[{"navn":"smør","antall":1,"sikkerhet":"høy","bokser":[
            {"x1":0,"y1":0,"x2":562,"y2":750},
            {"x1":900,"y1":1200,"x2":1300,"y2":1700}
        ]}]}"""

        val vare = AnalysePrompt.tolk(json, mapper, Bildestorrelse(1125, 1500)).single()

        assertThat(vare.bokser).containsExactly(Boks(0, 0, 500, 500), Boks(800, 800, 1000, 1000))
    }

    @Test
    fun `pikselprompten oppgir bildets størrelse`() {
        assertThat(AnalysePrompt.system(Boksformat.PIKSLER)).contains("i piksler")
        assertThat(AnalysePrompt.bruker(Bildestorrelse(1125, 1500)))
            .isEqualTo("List matvarene i kjøleskapet på bildet. Bildet er 1125 piksler bredt og 1500 piksler høyt.")
        assertThat(AnalysePrompt.bruker(null)).isEqualTo(AnalysePrompt.BRUKER)
    }
}
