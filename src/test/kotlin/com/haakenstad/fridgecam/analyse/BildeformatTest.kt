package com.haakenstad.fridgecam.analyse

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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

    @Test
    fun `leser bredde og høyde fra JPEG og PNG`() {
        assertThat(Bildeformat.storrelse(bilde("jpg", 1125, 1500))).isEqualTo(Bildestorrelse(1125, 1500))
        assertThat(Bildeformat.storrelse(bilde("png", 640, 480))).isEqualTo(Bildestorrelse(640, 480))
    }

    @Test
    fun `leser bredde og høyde fra WebP (VP8X og VP8L)`() {
        val vp8x = "RIFF\u0000\u0000\u0000\u0000WEBPVP8X".toByteArray() + ByteArray(8) +
            byteArrayOf(0x64, 0x04, 0x00, 0xDB.toByte(), 0x05, 0x00) // 1125-1, 1500-1, little-endian 24 bit
        assertThat(Bildeformat.storrelse(vp8x)).isEqualTo(Bildestorrelse(1125, 1500))

        // VP8L: 14 bit bredde-1 og 14 bit høyde-1 etter signaturbyten 0x2F.
        val bits = (1125 - 1) or ((1500 - 1) shl 14)
        val vp8l = "RIFF\u0000\u0000\u0000\u0000WEBPVP8L".toByteArray() + ByteArray(4) + byteArrayOf(0x2F) +
            byteArrayOf(bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte())
        assertThat(Bildeformat.storrelse(vp8l)).isEqualTo(Bildestorrelse(1125, 1500))
    }

    @Test
    fun `ukjent eller avkuttet filhode gir null`() {
        assertThat(Bildeformat.storrelse(AnalyseControllerTest.JPEG)).isNull()
        assertThat(Bildeformat.storrelse(bilde("jpg", 100, 100).copyOf(20))).isNull()
        assertThat(Bildeformat.storrelse("GIF89a".toByteArray())).isNull()
        assertThat(Bildeformat.storrelse(ByteArray(0))).isNull()
    }

    companion object {
        fun bilde(format: String, bredde: Int, hoyde: Int): ByteArray =
            ByteArrayOutputStream().also { ImageIO.write(BufferedImage(bredde, hoyde, BufferedImage.TYPE_INT_RGB), format, it) }
                .toByteArray()
    }
}
