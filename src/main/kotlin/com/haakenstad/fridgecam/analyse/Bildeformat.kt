package com.haakenstad.fridgecam.analyse

/**
 * Finner bildeformatet ut fra de første bytene i filen («magiske tall»).
 *
 * Content-Type fra klienten er bare en påstand. Det er bytene som sendes
 * videre til AI-leverandøren, så det er dem vi sjekker.
 */
/** Bildets størrelse i piksler. */
data class Bildestorrelse(val bredde: Int, val hoyde: Int)

object Bildeformat {

    /** @return `image/jpeg`, `image/png`, `image/webp`, eller null om formatet ikke støttes */
    fun finn(data: ByteArray): String? = when {
        starterMed(data, 0, JPEG) -> "image/jpeg"
        starterMed(data, 0, PNG) -> "image/png"
        starterMed(data, 0, RIFF) && starterMed(data, 8, WEBP) -> "image/webp"
        else -> null
    }

    /**
     * Bredde og høyde lest fra filhodet, uten å dekode bildet (#58: Claude oppgir
     * bokser i piksler, som må regnes om). Null hvis hodet ikke kan leses.
     */
    fun storrelse(data: ByteArray): Bildestorrelse? = try {
        when (finn(data)) {
            "image/jpeg" -> jpegStorrelse(data)
            "image/png" -> Bildestorrelse(int32(data, 16), int32(data, 20))
            "image/webp" -> webpStorrelse(data)
            else -> null
        }?.takeIf { it.bredde > 0 && it.hoyde > 0 }
    } catch (_: IndexOutOfBoundsException) {
        null
    }

    /** Leter etter SOF-markøren (baseline, utvidet eller progressiv). */
    private fun jpegStorrelse(data: ByteArray): Bildestorrelse? {
        var i = 2
        while (i + 9 < data.size) {
            if (u8(data, i) != 0xFF) return null
            val markor = u8(data, i + 1)
            if (markor == 0xFF) { i++; continue }
            if (markor in SOF_MARKORER) return Bildestorrelse(int16(data, i + 7), int16(data, i + 5))
            if (markor == 0xD9 || markor == 0xDA) return null
            i += 2 + int16(data, i + 2)
        }
        return null
    }

    private fun webpStorrelse(data: ByteArray): Bildestorrelse? = when (String(data, 12, 4, Charsets.US_ASCII)) {
        "VP8X" -> Bildestorrelse(1 + int24le(data, 24), 1 + int24le(data, 27))
        "VP8 " -> Bildestorrelse(int16le(data, 26) and 0x3FFF, int16le(data, 28) and 0x3FFF)
        "VP8L" -> {
            val b = int32le(data, 21)
            Bildestorrelse(1 + (b and 0x3FFF), 1 + ((b shr 14) and 0x3FFF))
        }
        else -> null
    }

    private val SOF_MARKORER = setOf(0xC0, 0xC1, 0xC2)

    private fun u8(d: ByteArray, i: Int) = d[i].toInt() and 0xFF
    private fun int16(d: ByteArray, i: Int) = (u8(d, i) shl 8) or u8(d, i + 1)
    private fun int32(d: ByteArray, i: Int) = (int16(d, i) shl 16) or int16(d, i + 2)
    private fun int16le(d: ByteArray, i: Int) = u8(d, i) or (u8(d, i + 1) shl 8)
    private fun int24le(d: ByteArray, i: Int) = int16le(d, i) or (u8(d, i + 2) shl 16)
    private fun int32le(d: ByteArray, i: Int) = int24le(d, i) or (u8(d, i + 3) shl 24)

    private fun starterMed(data: ByteArray, fra: Int, prefiks: ByteArray): Boolean =
        data.size >= fra + prefiks.size &&
            prefiks.indices.all { data[fra + it] == prefiks[it] }

    private val JPEG = bytes(0xFF, 0xD8, 0xFF)
    private val PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val RIFF = "RIFF".toByteArray()
    private val WEBP = "WEBP".toByteArray()

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
}
