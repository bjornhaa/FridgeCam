package com.haakenstad.fridgecam.analyse

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BildeformatTest {

    @Test
    fun `kjenner igjen JPEG, PNG og WebP`() {
        assertThat(Bildeformat.finn(AnalyseControllerTest.JPEG)).isEqualTo("image/jpeg")
        assertThat(Bildeformat.finn(AnalyseControllerTest.PNG)).isEqualTo("image/png")
        assertThat(Bildeformat.finn(AnalyseControllerTest.WEBP)).isEqualTo("image/webp")
    }

    @Test
    fun `avviser andre formater`() {
        assertThat(Bildeformat.finn("GIF89a".toByteArray())).isNull()
        assertThat(Bildeformat.finn("%PDF-1.7".toByteArray())).isNull()
        // RIFF uten WEBP er f.eks. en WAV-fil.
        assertThat(Bildeformat.finn("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray())).isNull()
    }

    @Test
    fun `tåler filer som er kortere enn signaturen`() {
        assertThat(Bildeformat.finn(ByteArray(0))).isNull()
        assertThat(Bildeformat.finn(byteArrayOf(0xFF.toByte()))).isNull()
    }
}
