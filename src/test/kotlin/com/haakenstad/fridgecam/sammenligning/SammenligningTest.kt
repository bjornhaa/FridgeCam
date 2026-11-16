package com.haakenstad.fridgecam.sammenligning

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.haakenstad.fridgecam.analyse.AnthropicVisionProvider
import com.haakenstad.fridgecam.analyse.Bildeformat
import com.haakenstad.fridgecam.analyse.FunnetVare
import com.haakenstad.fridgecam.analyse.GeminiVisionProvider
import com.haakenstad.fridgecam.analyse.Innsats
import com.haakenstad.fridgecam.analyse.Tokenforbruk
import com.haakenstad.fridgecam.analyse.VisionProvider
import com.haakenstad.fridgecam.common.ApiException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Kjører testbildene i `testbilder/` gjennom flere AI-oppsett og sammenligner
 * med fasiten (#11). Bruker ekte API-kall og koster derfor penger (Claude).
 *
 * Kjører bare med `FRIDGECAM_SAMMENLIGNING=1`, så verken CI eller vanlig
 * `mvn test` bruker AI-kreditt:
 *
 * ```
 * FRIDGECAM_SAMMENLIGNING=1 ANTHROPIC_API_KEY=… GEMINI_API_KEY=… \
 *   mvn test -Dtest=SammenligningTest -Dsurefire.failIfNoSpecifiedTests=false
 * ```
 *
 * Valgfritt:
 * - `FRIDGECAM_KJORINGER`: antall kjøringer per oppsett (standard 5)
 * - `FRIDGECAM_OPPSETT`: kommaseparert utvalg, f.eks. `claude-opus-medium,gemini-low`
 *   (standard: [STANDARD_OPPSETT])
 * - `FRIDGECAM_REVURDER=testbilder/kjoringer-….json`: teller på nytt fra lagrede
 *   råsvar, uten API-kall. Nyttig etter at fasit eller alias er rettet.
 *
 * Oppsett uten API-nøkkel hoppes over. Råsvar og rapport skrives til `testbilder/`.
 */
@EnabledIfEnvironmentVariable(named = "FRIDGECAM_SAMMENLIGNING", matches = "1")
class SammenligningTest {

    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()
    private val mappe = File("testbilder")

    private class Oppsett(val navn: String, val nokkel: String, val lag: (String) -> VisionProvider)

    private val alleOppsett = listOf(
        claude("claude-opus-medium", "claude-opus-5-5"),
        claude("claude-sonnet-medium", "claude-sonnet-5-5"),
        claude("claude-haiku-medium", "claude-haiku-5-5"),
        claude("claude-fable-medium", "claude-fable-5-2"),
        gemini("gemini-low", Innsats.LOW),
        gemini("gemini-medium", Innsats.MEDIUM),
        gemini("gemini-high", Innsats.HIGH),
    )

    private fun claude(navn: String, modell: String) = Oppsett(navn, "ANTHROPIC_API_KEY") { nokkel ->
        AnthropicVisionProvider(
            AnthropicOkHttpClient.builder().apiKey(nokkel).timeout(Duration.ofSeconds(180)).build(),
            modell, mapper, Innsats.MEDIUM,
        )
    }

    private fun gemini(navn: String, innsats: Innsats) = Oppsett(navn, "GEMINI_API_KEY") { nokkel ->
        GeminiVisionProvider(nokkel, GeminiVisionProvider.STANDARD_MODELL, mapper, innsats, timeout = Duration.ofSeconds(180))
    }

    /** Råsvaret fra én kjøring; lagres så kjøringen kan telles på nytt uten nye API-kall. */
    data class Kjoring(
        val bilde: String,
        val oppsett: String,
        val nr: Int,
        val sekunder: Double,
        val varer: List<FunnetVare>? = null,
        val forbruk: Tokenforbruk? = null,
        val feil: String? = null,
    )

    @Test
    fun `sammenlign AI-oppsett mot fasit`() {
        val tidspunkt = LocalDateTime.now()
        val stempel = tidspunkt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val fasiter = mappe.listFiles { f -> f.name.endsWith(".fasit.json") }.orEmpty()
            .map { mapper.readValue<Fasit>(it) }
            .associateBy { it.bilde }
        check(fasiter.isNotEmpty()) { "Fant ingen *.fasit.json i ${mappe.absolutePath}" }

        val revurder = System.getenv("FRIDGECAM_REVURDER")?.takeIf { it.isNotBlank() }
        val kjoringer: List<Kjoring> = if (revurder != null) {
            println("Teller på nytt fra $revurder (ingen API-kall)")
            mapper.readValue(File(revurder))
        } else {
            kjorAlle(fasiter.values.sortedBy { it.bilde }).also {
                val fil = File(mappe, "kjoringer-$stempel.json")
                mapper.writeValue(fil, it)
                println("Råsvar lagret i ${fil.absolutePath}")
            }
        }

        val rapport = lagRapport(kjoringer, fasiter, tidspunkt, revurder)
        val fil = File(mappe, "rapport-$stempel${if (revurder != null) "-revurdert" else ""}.md")
        fil.writeText(rapport)
        println()
        println(rapport)
        println("Rapport skrevet til ${fil.absolutePath}")
    }

    private fun kjorAlle(fasiter: List<Fasit>): List<Kjoring> {
        val antall = System.getenv("FRIDGECAM_KJORINGER")?.toIntOrNull() ?: 5
        val valgte = System.getenv("FRIDGECAM_OPPSETT")?.split(",")?.map { it.trim() }?.toSet() ?: STANDARD_OPPSETT
        val kjoringer = mutableListOf<Kjoring>()
        for (fasit in fasiter) {
            val bilde = File(mappe, fasit.bilde).readBytes()
            val mimeType = checkNotNull(Bildeformat.finn(bilde)) { "Ukjent bildeformat: ${fasit.bilde}" }
            for (oppsett in alleOppsett.filter { it.navn in valgte }) {
                val nokkel = System.getenv(oppsett.nokkel)
                if (nokkel.isNullOrBlank()) {
                    println("Hopper over ${oppsett.navn}: ${oppsett.nokkel} mangler")
                    continue
                }
                val provider = oppsett.lag(nokkel)
                for (nr in 1..antall) {
                    val k = kjorEn(provider, bilde, mimeType, fasit.bilde, oppsett.navn, nr)
                    val poeng = k.varer?.let { Poengsetter.vurder(fasit, it) }
                    println("${oppsett.navn} #$nr: ${k.feil ?: "${poeng!!.treff.size}/${fasit.varer.size} treff, ${poeng.bomskudd.size} bomskudd"} (${tall(k.sekunder, 1)} s)")
                    kjoringer += k
                }
                (provider as? AutoCloseable)?.close()
            }
        }
        return kjoringer
    }

    private fun kjorEn(provider: VisionProvider, bilde: ByteArray, mimeType: String, bildenavn: String, oppsett: String, nr: Int): Kjoring {
        repeat(3) { forsok ->
            val start = System.nanoTime()
            try {
                val r = provider.analyser(bilde, mimeType)
                return Kjoring(bildenavn, oppsett, nr, sekunder(start), r.varer, r.forbruk)
            } catch (e: ApiException) {
                // Gemini har lave grenser på gratisnivået og er tidvis utilgjengelig; vent og prøv igjen.
                if (e.code in setOf("rate_limit", "ai_utilgjengelig") && forsok < 2) {
                    println("  ${e.code}, venter 20 s …")
                    Thread.sleep(20_000)
                } else {
                    return Kjoring(bildenavn, oppsett, nr, sekunder(start), feil = e.code)
                }
            }
        }
        error("uoppnåelig")
    }

    private fun lagRapport(kjoringer: List<Kjoring>, fasiter: Map<String, Fasit>, tidspunkt: LocalDateTime, revurder: String?): String {
        val sb = StringBuilder()
        sb.appendLine("# Sammenligning av AI-oppsett")
        sb.appendLine()
        sb.appendLine(tidspunkt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
            (revurder?.let { " · telt på nytt fra `${File(it).name}`" } ?: ""))

        for ((bilde, perBilde) in kjoringer.groupBy { it.bilde }) {
            val fasit = fasiter[bilde] ?: continue
            sb.appendLine()
            sb.appendLine("## $bilde (${fasit.varer.size} varer i fasiten, ${fasit.varer.count { it.vanskelig }} vanskelige)")
            sb.appendLine()
            sb.appendLine("| Oppsett | Treff (snitt) | Dekning tydelige | Dekning vanskelige | Bomskudd (snitt) | Presisjon | Tid (snitt) | Kostnad (snitt) | Feil |")
            sb.appendLine("|---|---|---|---|---|---|---|---|---|")
            val perOppsett = perBilde.groupBy { it.oppsett }
            perOppsett.forEach { (navn, liste) -> sb.appendLine(sammendragsrad(navn, liste, fasit)) }
            perOppsett.forEach { (navn, liste) -> sb.append(detaljer(navn, liste, fasit)) }
        }
        return sb.toString()
    }

    private fun sammendragsrad(navn: String, kjoringer: List<Kjoring>, fasit: Fasit): String {
        val ok = kjoringer.filter { it.varer != null }
        val p = ok.map { Poengsetter.vurder(fasit, it.varer!!) }
        fun snitt(f: (Poeng) -> Double) = if (p.isEmpty()) Double.NaN else p.map(f).average()
        val kostnad = ok.mapNotNull { it.forbruk?.kostnadUsd }.takeIf { it.isNotEmpty() }?.average()
        return "| $navn " +
            "| ${tall(snitt { it.treff.size.toDouble() }, 1)}/${fasit.varer.size} " +
            "| ${prosent(snitt { it.dekning(vanskelig = false) })} " +
            "| ${prosent(snitt { it.dekning(vanskelig = true) })} " +
            "| ${tall(snitt { it.bomskudd.size.toDouble() }, 1)} " +
            "| ${prosent(snitt { it.presisjon })} " +
            "| ${tall(ok.map { it.sekunder }.average(), 1)} s " +
            "| ${kostnad?.let { "$" + tall(it, 4) } ?: "–"} " +
            "| ${kjoringer.count { it.feil != null }} |"
    }

    private fun detaljer(navn: String, kjoringer: List<Kjoring>, fasit: Fasit): String {
        val ok = kjoringer.filter { it.varer != null }
        val p = ok.map { Poengsetter.vurder(fasit, it.varer!!) }
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("### $navn")
        sb.appendLine()
        val mangler = p.flatMap { it.mangler }.groupingBy { it.navn }.eachCount()
        sb.appendLine("**Manglet** (antall kjøringer av ${p.size}): " +
            fasit.varer.filter { it.navn in mangler }
                .joinToString { "${it.navn}${if (it.vanskelig) " (vanskelig)" else ""} ×${mangler[it.navn]}" }
                .ifEmpty { "ingen" })
        sb.appendLine()
        val bom = p.flatMap { it.bomskudd }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        sb.appendLine("**Bomskudd** (varer som ikke er i fasiten): " +
            bom.joinToString { "${it.key} ×${it.value}" }.ifEmpty { "ingen" })
        val feil = kjoringer.mapNotNull { it.feil }
        if (feil.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("**Feil:** ${feil.groupingBy { it }.eachCount().entries.joinToString { "${it.key} ×${it.value}" }}")
        }
        sb.appendLine()
        sb.appendLine("<details><summary>Navn AI-en brukte, per kjøring</summary>")
        sb.appendLine()
        for (k in ok) {
            sb.appendLine("- #${k.nr}: " + k.varer!!.joinToString { if (it.antall > 1) "${it.navn} (${it.antall})" else it.navn })
        }
        sb.appendLine()
        sb.appendLine("</details>")
        return sb.toString()
    }

    private fun sekunder(start: Long) = (System.nanoTime() - start) / 1e9

    private fun tall(v: Double, desimaler: Int) = if (v.isNaN()) "–" else "%.${desimaler}f".format(Locale.ROOT, v)

    private fun prosent(v: Double) = if (v.isNaN()) "–" else "${Math.round(v * 100)} %"

    companion object {
        val STANDARD_OPPSETT = setOf(
            "claude-opus-medium", "claude-sonnet-medium", "claude-haiku-medium", "claude-fable-medium", "gemini-medium",
        )
    }
}
