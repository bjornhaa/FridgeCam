package com.haakenstad.fridgecam.skanning

import com.haakenstad.fridgecam.DatabaseTest
import com.haakenstad.fridgecam.analyse.FunnetVare
import com.haakenstad.fridgecam.analyse.Sikkerhet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Bare siste bilde lagres (#43): den nyeste skanningens og beholdningens.
 * Skanningene selv beholdes alltid.
 */
class BildeOppryddingTest : DatabaseTest() {

    @Autowired private lateinit var skanninger: SkanningRepository
    @Autowired private lateinit var service: SkanningService

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    private fun skann(eier: Eier, kjoleskapId: Long = eier.kjoleskapId): Long = skanninger.opprett(
        NySkanning(eier.husstandId, kjoleskapId, eier.brukerId, "fake", null, listOf(FunnetVare("melk", 1, Sikkerhet.HOY)), Bilde("image/jpeg", jpeg)),
    )

    private fun lagre(eier: Eier, skanningId: Long) =
        service.lagreSomBeholdning(eier.brukerId, skanningId, listOf(RettetVare("melk", 1, 0)))

    private fun harBilde(id: Long) = skanninger.hentBilde(id) != null

    @Test
    fun `ny skanning sletter de eldre bildene, men beholder skanningene`() {
        val eier = nyEier()
        val ider = (1..3).map { skann(eier) }

        assertThat(ider.map(::harBilde)).containsExactly(false, false, true)
        assertThat(ider.map { skanninger.finn(it) }).doesNotContainNull()
    }

    @Test
    fun `kjøleskapets bilde beholdes mens en ny skanning rettes, og slettes når den nye lagres`() {
        val eier = nyEier()
        val iKjoleskapet = skann(eier)
        lagre(eier, iKjoleskapet)

        val ny = skann(eier)
        assertThat(harBilde(iKjoleskapet)).isTrue()
        assertThat(harBilde(ny)).isTrue()

        lagre(eier, ny)
        assertThat(harBilde(iKjoleskapet)).isFalse()
        assertThat(harBilde(ny)).isTrue()
    }

    @Test
    fun `skanninger som aldri lagres tar ikke med seg kjøleskapets bilde`() {
        val eier = nyEier()
        val iKjoleskapet = skann(eier)
        lagre(eier, iKjoleskapet)
        val forlatt = skann(eier)

        val nyeste = skann(eier)

        assertThat(harBilde(iKjoleskapet)).isTrue()
        assertThat(harBilde(forlatt)).isFalse()
        assertThat(harBilde(nyeste)).isTrue()
    }

    @Test
    fun `bildet i et annet kjøleskap i husstanden røres ikke`() {
        val eier = nyEier()
        val hytta = kjoleskapRepo.opprett(eier.husstandId, "Hytta")
        val paHytta = skann(eier, hytta)
        lagre(eier, paHytta)

        val hjemme = (1..3).map { skann(eier) }
        lagre(eier, hjemme.last())

        assertThat(harBilde(paHytta)).isTrue()
        assertThat(hjemme.map(::harBilde)).containsExactly(false, false, true)
    }

    @Test
    fun `andre husstanders bilder røres ikke`() {
        val andre = nyEier()
        val andresSkanning = skann(andre)
        val meg = nyEier()
        repeat(3) { skann(meg) }

        assertThat(harBilde(andresSkanning)).isTrue()
    }
}
