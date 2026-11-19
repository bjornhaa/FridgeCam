package com.haakenstad.fridgecam.skanning

import com.haakenstad.fridgecam.ainokkel.AiNokkelService
import com.haakenstad.fridgecam.analyse.FunnetVare
import com.haakenstad.fridgecam.analyse.Tokenforbruk
import com.haakenstad.fridgecam.analyse.VisionProviderFabrikk
import com.haakenstad.fridgecam.beholdning.BeholdningRepository
import com.haakenstad.fridgecam.beholdning.NyBeholdningVare
import com.haakenstad.fridgecam.common.ApiException
import com.haakenstad.fridgecam.husstand.HusstandTilgang
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class AnalysertSkanning(
    val skanningId: Long,
    val varer: List<FunnetVare>,
    val forbruk: Tokenforbruk?,
    /** Leverandøren som svarte, f.eks. «anthropic/claude-opus-5-5 (effort medium)». */
    val leverandor: String,
)

@Service
class SkanningService(
    private val fabrikk: VisionProviderFabrikk,
    private val aiNokler: AiNokkelService,
    private val skanninger: SkanningRepository,
    private val beholdning: BeholdningRepository,
    private val tilgang: HusstandTilgang,
) {

    /**
     * Sender bildet til AI-en og lagrer skanningen med en gang: bilde, AI-ens
     * svar og forbruk. Da vet vi hva hvert kall kostet, og brukeren slipper å
     * laste opp bildet på nytt når listen lagres. Ingen transaksjon rundt
     * AI-kallet, som kan ta et halvt minutt.
     *
     * Kallet går med husstandens egen AI-nøkkel (#42); uten nøkkel stopper det
     * med `mangler_ai_nokkel` før bildet sendes noe sted.
     */
    fun analyser(brukerId: Long, bilde: ByteArray, mimeType: String): AnalysertSkanning {
        val husstandId = tilgang.husstandFor(brukerId)
        val nokkel = if (fabrikk.kreverNokkel) aiNokler.nokkelFor(husstandId) else null
        val (navn, resultat) = fabrikk.lag(nokkel).use { it.navn to it.analyser(bilde, mimeType) }
        val id = skanninger.opprett(
            NySkanning(husstandId, brukerId, navn, resultat.forbruk, resultat.varer, Bilde(mimeType, bilde)),
        )
        return AnalysertSkanning(id, resultat.varer, resultat.forbruk, navn)
    }

    /** Bildet til en skanning, bare for medlemmer av husstanden (#33). */
    fun hentBilde(brukerId: Long, skanningId: Long): Bilde {
        val skanning = skanninger.finn(skanningId) ?: throw HusstandTilgang.ikkeFunnet()
        tilgang.sjekkMedlem(brukerId, skanning.husstandId)
        return skanninger.hentBilde(skanningId) ?: throw HusstandTilgang.ikkeFunnet()
    }

    /**
     * Erstatter husstandens beholdning med brukerens rettede liste (#32).
     * Boksene hentes fra AI-ens svar, ikke fra klienten.
     */
    @Transactional
    fun lagreSomBeholdning(brukerId: Long, skanningId: Long, varer: List<RettetVare>) {
        val skanning = skanninger.finn(skanningId) ?: throw HusstandTilgang.ikkeFunnet()
        tilgang.sjekkMedlem(brukerId, skanning.husstandId)

        val nye = varer.map { vare ->
            val bokser = vare.aiIndeks?.let { indeks ->
                skanning.aiVarer.getOrNull(indeks)?.bokser
                    ?: throw ApiException(HttpStatus.BAD_REQUEST, "ugyldig_foresporsel", "Ukjent vare fra AI-en.")
            } ?: emptyList()
            NyBeholdningVare(vare.navn.trim(), vare.antall, bokser)
        }
        beholdning.erstatt(skanning.husstandId, skanningId, nye)
        skanninger.markerLagret(skanningId, varer.map { it.copy(navn = it.navn.trim()) })
        // Den forrige beholdningens bilde trengs ikke lenger (#43).
        skanninger.slettGamleBilder(skanning.husstandId)
    }
}
