package com.haakenstad.fridgecam.sammenligning

import com.haakenstad.fridgecam.analyse.FunnetVare
import com.haakenstad.fridgecam.analyse.Sikkerhet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PoengsetterTest {

    private val fasit = Fasit(
        bilde = "test.jpg",
        varer = listOf(
            FasitVare("smør", alias = listOf("meierismør")),
            FasitVare("brelett", alias = listOf("smør", "margarin")),
            FasitVare("gulost", alias = listOf("ost")),
            FasitVare("rester i skål", vanskelig = true, alias = listOf("rester")),
        ),
    )

    private fun vare(navn: String) = FunnetVare(navn, 1, Sikkerhet.HOY)

    @Test
    fun `teller treff, mangler og bomskudd`() {
        val poeng = Poengsetter.vurder(fasit, listOf(vare("smør"), vare("ost"), vare("ananas")))

        assertThat(poeng.treff.map { it.navn }).containsExactly("smør", "gulost")
        assertThat(poeng.mangler.map { it.navn }).containsExactly("brelett", "rester i skål")
        assertThat(poeng.bomskudd).containsExactly("ananas")
        assertThat(poeng.presisjon).isEqualTo(2.0 / 3)
        assertThat(poeng.dekning).isEqualTo(0.5)
    }

    @Test
    fun `samme navn kan treffe flere fasitvarer, hovednavn først`() {
        val poeng = Poengsetter.vurder(fasit, listOf(vare("smør"), vare("smør")))

        // Første «smør» går til smør (hovednavn), andre til Brelett (alias).
        assertThat(poeng.treff.map { it.navn }).containsExactly("smør", "brelett")
        assertThat(poeng.duplikater).isEmpty()
    }

    @Test
    fun `generelt navn tar ikke en vare som har eget treff senere i listen`() {
        val poeng = Poengsetter.vurder(fasit, listOf(vare("ost"), vare("gulost")))

        // «gulost» treffer hovednavnet; «ost» har da ingen ledig vare igjen og blir duplikat.
        assertThat(poeng.treff.map { it.navn }).containsExactly("gulost")
        assertThat(poeng.duplikater).containsExactly("ost")
    }

    @Test
    fun `ekstra treff på en vare som er funnet er duplikat, ikke bomskudd`() {
        val poeng = Poengsetter.vurder(fasit, listOf(vare("gulost"), vare("ost")))

        assertThat(poeng.treff.map { it.navn }).containsExactly("gulost")
        assertThat(poeng.duplikater).containsExactly("ost")
        assertThat(poeng.bomskudd).isEmpty()
    }

    @Test
    fun `navn sammenlignes uten store bokstaver, mellomrom og bindestrek`() {
        val poeng = Poengsetter.vurder(fasit, listOf(vare("Rester i skål"), vare("Meieri-smør")))

        assertThat(poeng.treff.map { it.navn }).containsExactly("rester i skål", "smør")
    }

    @Test
    fun `dekning regnes for seg for vanskelige og tydelige varer`() {
        val poeng = Poengsetter.vurder(fasit, listOf(vare("rester")))

        assertThat(poeng.dekning(vanskelig = true)).isEqualTo(1.0)
        assertThat(poeng.dekning(vanskelig = false)).isEqualTo(0.0)
    }
}
