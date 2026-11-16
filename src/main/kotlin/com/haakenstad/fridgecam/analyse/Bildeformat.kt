package com.haakenstad.fridgecam.analyse

/**
 * Finner bildeformatet ut fra de første bytene i filen («magiske tall»).
 *
 * Content-Type fra klienten er bare en påstand. Det er bytene som sendes
 * videre til AI-leverandøren, så det er dem vi sjekker.
 */
object Bildeformat {

    /** @return `image/jpeg`, `image/png`, `image/webp`, eller null om formatet ikke støttes */
    fun finn(data: ByteArray): String? = when {
        starterMed(data, 0, JPEG) -> "image/jpeg"
        starterMed(data, 0, PNG) -> "image/png"
        starterMed(data, 0, RIFF) && starterMed(data, 8, WEBP) -> "image/webp"
        else -> null
    }

    private fun starterMed(data: ByteArray, fra: Int, prefiks: ByteArray): Boolean =
        data.size >= fra + prefiks.size &&
            prefiks.indices.all { data[fra + it] == prefiks[it] }

    private val JPEG = bytes(0xFF, 0xD8, 0xFF)
    private val PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val RIFF = "RIFF".toByteArray()
    private val WEBP = "WEBP".toByteArray()

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
}
