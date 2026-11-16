package com.haakenstad.fridgecam.analyse

import com.haakenstad.fridgecam.common.GlobalExceptionHandler
import com.haakenstad.fridgecam.innlogging.JwtService
import com.haakenstad.fridgecam.innlogging.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMultipartHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart

@WebMvcTest(
    AnalyseController::class,
    properties = ["fridgecam.jwt.hemmelighet=en-testhemmelighet-som-er-lang-nok-for-hs256"],
    // Som i FridgeCamApplication: ingen ubrukt standardbruker med generert passord.
    excludeAutoConfiguration = [UserDetailsServiceAutoConfiguration::class],
)
@Import(GlobalExceptionHandler::class, SecurityConfig::class, JwtService::class, AnalyseControllerTest.Config::class)
class AnalyseControllerTest(
    @Autowired private val mvc: MockMvc,
    @Autowired jwt: JwtService,
) {

    private val token = jwt.lag(1)

    @TestConfiguration
    class Config {
        @Bean
        fun visionProvider(): VisionProvider = FakeVisionProvider()
    }

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
