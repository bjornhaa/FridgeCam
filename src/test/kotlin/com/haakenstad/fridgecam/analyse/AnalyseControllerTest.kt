package com.haakenstad.fridgecam.analyse

import com.haakenstad.fridgecam.AppTest
import com.haakenstad.fridgecam.skanning.SkanningRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMultipartHttpServletRequestDsl
import org.springframework.test.web.servlet.multipart

/**
 * `/api/analyze` mot hele appen med fake-leverandøren: validering av bildet,
 * innlogging, og at skanningen lagres.
 */
class AnalyseControllerTest : AppTest() {

    @Autowired
    private lateinit var skanninger: SkanningRepository

    private val token by lazy { registrer() }

    @Test
    fun `uten innlogging avvises forespørselen før bildet sendes til AI`() {
        mvc.multipart("/api/analyze") { file(bilde(JPEG)) }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("ikke_innlogget") }
            }
    }

    @Test
    fun `JPEG gir varelisten fra leverandøren`() {
        mvc.multipart("/api/analyze") { innlogget(); file(bilde(JPEG)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.skanningId") { isNumber() }
                jsonPath("$.leverandor") { value("fake") }
                jsonPath("$.varer.length()") { value(FakeVisionProvider.VARER.size) }
                jsonPath("$.varer[0].navn") { value("melk") }
                jsonPath("$.varer[0].antall") { value(1) }
                jsonPath("$.varer[0].sikkerhet") { value("høy") }
                jsonPath("$.varer[0].bokser[0].x1") { value(80) }
                jsonPath("$.varer[0].bokser[0].y2") { value(420) }
            }
    }

    @Test
    fun `skanningen lagres med bilde og AI-svar`() {
        val svar = mvc.multipart("/api/analyze") { innlogget(); file(bilde(JPEG)) }
            .andReturn().response.contentAsString
        val id = Regex("\"skanningId\":(\\d+)").find(svar)!!.groupValues[1].toLong()

        val skanning = skanninger.finn(id)!!
        assertThat(skanning.leverandor).isEqualTo("fake")
        assertThat(skanning.aiVarer).isEqualTo(FakeVisionProvider.VARER)
        assertThat(skanning.lagret).isNull()
        assertThat(skanninger.hentBilde(id)!!.data).isEqualTo(JPEG)
    }

    @Test
    fun `PNG og WebP godtas`() {
        for (data in listOf(PNG, WEBP)) {
            mvc.multipart("/api/analyze") { innlogget(); file(bilde(data)) }
                .andExpect { status { isOk() } }
        }
    }

    @Test
    fun `fil som bare later som den er et bilde avvises`() {
        val tekst = "dette er ikke et bilde".toByteArray()

        mvc.multipart("/api/analyze") { innlogget(); file(bilde(tekst, contentType = "image/jpeg")) }
            .andExpect {
                status { isUnsupportedMediaType() }
                jsonPath("$.code") { value("ugyldig_bildeformat") }
            }
    }

    @Test
    fun `tomt bilde avvises`() {
        mvc.multipart("/api/analyze") { innlogget(); file(bilde(ByteArray(0))) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("bilde_mangler") }
            }
    }

    @Test
    fun `forespørsel uten bilde avvises`() {
        mvc.multipart("/api/analyze") { innlogget(); file(MockMultipartFile("noe_annet", "x.jpg", "image/jpeg", JPEG)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("bilde_mangler") }
            }
    }

    private fun MockMultipartHttpServletRequestDsl.innlogget() {
        header("Authorization", "Bearer $token")
    }

    private fun bilde(data: ByteArray, contentType: String = "image/jpeg") =
        MockMultipartFile("bilde", "kjoleskap.jpg", contentType, data)

    companion object {
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
        val WEBP = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()
    }
}
