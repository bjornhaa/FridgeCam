package com.haakenstad.fridgecam.skanning

import com.haakenstad.fridgecam.DatabaseTest
import com.haakenstad.fridgecam.analyse.Boks
import com.haakenstad.fridgecam.analyse.FunnetVare
import com.haakenstad.fridgecam.analyse.Sikkerhet
import com.haakenstad.fridgecam.analyse.Tokenforbruk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SkanningRepositoryTest : DatabaseTest() {

    @Autowired private lateinit var skanninger: SkanningRepository

    private val varer = listOf(
        FunnetVare("melk", 2, Sikkerhet.HOY, listOf(Boks(10, 20, 300, 400), Boks(310, 20, 600, 400))),
        FunnetVare("brunost", 1, Sikkerhet.LAV),
    )
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    private fun ny(eier: Eier, forbruk: Tokenforbruk? = Tokenforbruk("claude-opus-5-5", 1800, 950, 0.0332)) =
        NySkanning(eier.husstandId, eier.brukerId, "anthropic/claude-opus-5-5", forbruk, varer, Bilde("image/jpeg", jpeg))

    @Test
    fun `skanning lagres og leses tilbake med AI-svar og forbruk`() {
        val eier = nyEier()

        val id = skanninger.opprett(ny(eier))
        val skanning = skanninger.finn(id)!!

        assertThat(skanning.husstandId).isEqualTo(eier.husstandId)
        assertThat(skanning.brukerId).isEqualTo(eier.brukerId)
        assertThat(skanning.leverandor).isEqualTo("anthropic/claude-opus-5-5")
        assertThat(skanning.forbruk).isEqualTo(Tokenforbruk("claude-opus-5-5", 1800, 950, 0.0332))
        assertThat(skanning.aiVarer).isEqualTo(varer)
        assertThat(skanning.tidspunkt).isNotNull()
    }

    @Test
    fun `sikkerhet lagres som den norske verdien i JSON`() {
        val id = skanninger.opprett(ny(nyEier()))

        val lagret = jdbc.queryForObject(
            "SELECT ai_varer -> 1 ->> 'sikkerhet' FROM skanning WHERE id = :id",
            mapOf("id" to id),
            String::class.java,
        )

        assertThat(lagret).isEqualTo("lav")
    }

    @Test
    fun `skanning uten kjent forbruk lagres med tomme felt`() {
        val id = skanninger.opprett(ny(nyEier(), forbruk = null))

        assertThat(skanninger.finn(id)!!.forbruk).isNull()
    }

    @Test
    fun `bildet lagres for seg og kommer tilbake byte for byte`() {
        val id = skanninger.opprett(ny(nyEier()))

        val bilde = skanninger.hentBilde(id)!!

        assertThat(bilde.mimeType).isEqualTo("image/jpeg")
        assertThat(bilde.data).isEqualTo(jpeg)
    }

    @Test
    fun `siste gir husstandens nyeste skanning, ikke andres`() {
        val eier = nyEier()
        val annen = nyEier()
        skanninger.opprett(ny(eier))
        val nyeste = skanninger.opprett(ny(eier))
        skanninger.opprett(ny(annen))

        assertThat(skanninger.siste(eier.husstandId)!!.id).isEqualTo(nyeste)
        assertThat(skanninger.siste(nyEier().husstandId)).isNull()
    }

    @Test
    fun `sletting av skanning fjerner bildet`() {
        val id = skanninger.opprett(ny(nyEier()))

        jdbc.update("DELETE FROM skanning WHERE id = :id", mapOf("id" to id))

        assertThat(skanninger.hentBilde(id)).isNull()
    }

    @Test
    fun `skanningen beholdes når brukeren slettes, uten bruker`() {
        val eier = nyEier()
        val id = skanninger.opprett(ny(eier))

        jdbc.update("DELETE FROM bruker WHERE id = :id", mapOf("id" to eier.brukerId))

        assertThat(skanninger.finn(id)!!.brukerId).isNull()
    }
}
