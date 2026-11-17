package com.haakenstad.fridgecam.beholdning

import com.haakenstad.fridgecam.DatabaseTest
import com.haakenstad.fridgecam.analyse.Boks
import com.haakenstad.fridgecam.skanning.Bilde
import com.haakenstad.fridgecam.skanning.NySkanning
import com.haakenstad.fridgecam.skanning.SkanningRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException

class BeholdningRepositoryTest : DatabaseTest() {

    @Autowired private lateinit var beholdning: BeholdningRepository
    @Autowired private lateinit var skanninger: SkanningRepository

    private fun skanning(eier: Eier): Long =
        skanninger.opprett(
            NySkanning(eier.husstandId, eier.brukerId, "fake", null, emptyList(), Bilde("image/jpeg", byteArrayOf(1))),
        )

    @Test
    fun `varene lagres i rekkefølge med bokser og skanning`() {
        val eier = nyEier()
        val s = skanning(eier)

        beholdning.erstatt(
            eier.husstandId, s,
            listOf(
                NyBeholdningVare("melk", 2, listOf(Boks(1, 2, 3, 4))),
                NyBeholdningVare("smør", 1),
            ),
        )
        val varer = beholdning.forHusstand(eier.husstandId)

        assertThat(varer.map { it.navn }).containsExactly("melk", "smør")
        assertThat(varer[0].antall).isEqualTo(2)
        assertThat(varer[0].bokser).containsExactly(Boks(1, 2, 3, 4))
        assertThat(varer[1].bokser).isEmpty()
        assertThat(varer).allMatch { it.skanningId == s }
    }

    @Test
    fun `ny skanning erstatter hele beholdningen`() {
        val eier = nyEier()
        beholdning.erstatt(eier.husstandId, skanning(eier), listOf(NyBeholdningVare("melk", 1), NyBeholdningVare("ost", 1)))

        beholdning.erstatt(eier.husstandId, skanning(eier), listOf(NyBeholdningVare("juice", 3)))

        assertThat(beholdning.forHusstand(eier.husstandId).map { it.navn }).containsExactly("juice")
    }

    @Test
    fun `tom liste tømmer beholdningen`() {
        val eier = nyEier()
        beholdning.erstatt(eier.husstandId, skanning(eier), listOf(NyBeholdningVare("melk", 1)))

        beholdning.erstatt(eier.husstandId, skanning(eier), emptyList())

        assertThat(beholdning.forHusstand(eier.husstandId)).isEmpty()
    }

    @Test
    fun `andre husstanders beholdning røres ikke`() {
        val eier = nyEier()
        val nabo = nyEier()
        beholdning.erstatt(nabo.husstandId, skanning(nabo), listOf(NyBeholdningVare("egg", 6)))

        beholdning.erstatt(eier.husstandId, skanning(eier), listOf(NyBeholdningVare("melk", 1)))

        assertThat(beholdning.forHusstand(nabo.husstandId).map { it.navn }).containsExactly("egg")
    }

    @Test
    fun `antall må være minst 1 og navn kan ikke være tomt`() {
        val eier = nyEier()
        val s = skanning(eier)

        assertThatThrownBy { beholdning.erstatt(eier.husstandId, s, listOf(NyBeholdningVare("melk", 0))) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { beholdning.erstatt(eier.husstandId, s, listOf(NyBeholdningVare("  ", 1))) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `feil midt i erstatningen beholder den gamle beholdningen`() {
        val eier = nyEier()
        beholdning.erstatt(eier.husstandId, skanning(eier), listOf(NyBeholdningVare("melk", 1)))

        assertThatThrownBy {
            beholdning.erstatt(eier.husstandId, skanning(eier), listOf(NyBeholdningVare("ost", 1), NyBeholdningVare("egg", 0)))
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        assertThat(beholdning.forHusstand(eier.husstandId).map { it.navn }).containsExactly("melk")
    }

    @Test
    fun `beholdningen overlever at skanningen slettes`() {
        val eier = nyEier()
        val s = skanning(eier)
        beholdning.erstatt(eier.husstandId, s, listOf(NyBeholdningVare("melk", 1)))

        jdbc.update("DELETE FROM skanning WHERE id = :id", mapOf("id" to s))

        assertThat(beholdning.forHusstand(eier.husstandId).single().skanningId).isNull()
    }
}
