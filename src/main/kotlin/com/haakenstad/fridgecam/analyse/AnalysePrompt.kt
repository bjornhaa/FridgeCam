package com.haakenstad.fridgecam.analyse

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Hvordan modellen oppgir bokser. Varelisten er lik for alle leverandører; bare
 * boksene skiller seg, fordi Gemini er trent på sitt eget `box_2d`-format og
 * treffer best med det.
 */
enum class Boksformat {
    /** `{"x1":…, "y1":…, "x2":…, "y2":…}`: øvre venstre og nedre høyre hjørne. */
    HJORNER,

    /** `[ymin, xmin, ymax, xmax]`, Geminis `box_2d`-format. */
    BOX_2D,

    /**
     * Som [HJORNER], men i piksler i bildet (#58). Claude oppgir piksler selv når
     * den blir bedt om 0–1000, så den får bildets størrelse og spørres om det den
     * faktisk gjør; backend regner om til 0–1000.
     */
    PIKSLER,
}

/**
 * Prompt, JSON-skjema og tolking av svaret, felles for alle AI-leverandører.
 * Da gir Anthropic og Gemini svar i samme form, og valideringen skjer ett sted.
 */
object AnalysePrompt {

    private val GRUNNREGLER = """
        Du ser på et bilde av innholdet i et kjøleskap og lister matvarene du ser.

        Regler:
        - Bruk vanlige norske varenavn i entall og små bokstaver, f.eks. «melk», «egg», «gulost», «paprika».
        - Ta med merke eller variant bare når det skiller varer fra hverandre, f.eks. «lettmelk» og «helmelk».
        - Antall er antall forpakninger eller enheter du faktisk ser, ikke innholdet i dem.
          En eggekartong med synlige egg teller som antall egg; en lukket kartong teller som 1.
        - Sikkerhet:
          - «høy»: varen er tydelig og du er sikker på hva den er.
          - «middels»: du ser varen, men navn eller antall er usikkert.
          - «lav»: varen er delvis skjult eller utydelig, eller navnet er et gjett.
        - Bruk alt du ser for å avgjøre hva varen er: tekst og merkenavn på etiketten, og
          emballasjens form, farge og utseende. Står det f.eks. «Rømme» eller «Kokosmelk» på pakken,
          er det det varen er; teksten veier tyngst når den er lesbar. Merkenavn som brukes som
          varenavn, f.eks. «Biola» eller «Kesam», kan brukes som navn.
        - Bilder og illustrasjoner på emballasjen viser ofte serveringsforslag, ikke innholdet.
          En boks med bilde av en pizza er ikke en pizza; bestem varen ut fra tekst og emballasje.
        - Gi alltid et konkret varenavn. Er du usikker, gi ditt beste gjett ut fra form, farge og
          plassering, og sett sikkerhet «lav». Brukeren retter heller et gjett enn å skrive navnet selv.
          Bruk generelle ord som «beger», «pakke» eller «flaske» bare når du ikke har noe grunnlag for et gjett.
        - Ugjennomsiktige bokser og matbokser uten synlig innhold tas med som «matboks» med lav sikkerhet.
        - Ikke ta med selve kjøleskapet, hyller, skuffer eller emballasje uten innhold.
        - Ser du ingen matvarer, returner en tom liste.

        Plassering («bokser»):
        - Oppgi hvor varen er i bildet med en boks per synlig eksemplar som stramt omslutter varen.
        - Står mange like varer samlet (f.eks. egg i en kartong), holder én boks rundt gruppen.
    """.trimIndent()

    private val KOORDINATER = mapOf(
        Boksformat.HJORNER to """
            - Koordinatene er heltall fra 0 til 1000 relativt til bildets bredde og høyde,
              med origo øverst til venstre: x1,y1 er øvre venstre hjørne og x2,y2 nedre høyre hjørne.
              Midten av bildet er altså x=500, y=500, uavhengig av bildets størrelse og format.
        """.trimIndent(),
        Boksformat.BOX_2D to """
            - Hver boks er en box_2d: [ymin, xmin, ymax, xmax], heltall normalisert til 0–1000.
        """.trimIndent(),
        Boksformat.PIKSLER to """
            - Koordinatene er heltall i piksler i bildet, med origo øverst til venstre:
              x1,y1 er øvre venstre hjørne og x2,y2 nedre høyre hjørne.
              Bildets bredde og høyde i piksler står i meldingen.
        """.trimIndent(),
    )

    fun system(format: Boksformat): String = GRUNNREGLER + "\n" + KOORDINATER.getValue(format)

    val SYSTEM = system(Boksformat.HJORNER)

    const val BRUKER = "List matvarene i kjøleskapet på bildet."

    /** Brukermeldingen; med [Boksformat.PIKSLER] står bildets størrelse i den. */
    fun bruker(storrelse: Bildestorrelse?): String =
        if (storrelse == null) BRUKER
        else "$BRUKER Bildet er ${storrelse.bredde} piksler bredt og ${storrelse.hoyde} piksler høyt."

    private val HJORNE_SKJEMA = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("x1", "y1", "x2", "y2"),
        "properties" to listOf("x1", "y1", "x2", "y2").associateWith { mapOf("type" to "integer") },
    )

    private val BOKS_SKJEMA = mapOf(
        Boksformat.HJORNER to HJORNE_SKJEMA,
        Boksformat.PIKSLER to HJORNE_SKJEMA,
        Boksformat.BOX_2D to mapOf(
            "type" to "array",
            "description" to "box_2d: [ymin, xmin, ymax, xmax], 0–1000",
            "items" to mapOf("type" to "integer"),
            "minItems" to 4,
            "maxItems" to 4,
        ),
    )

    /** JSON-skjema for svaret. Holdt enkelt så alle leverandørene støtter det. */
    fun skjema(format: Boksformat): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("varer"),
        "properties" to mapOf(
            "varer" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("navn", "antall", "sikkerhet", "bokser"),
                    "properties" to mapOf(
                        "navn" to mapOf("type" to "string", "description" to "Norsk varenavn i entall"),
                        "antall" to mapOf("type" to "integer", "description" to "Antall synlige enheter, minst 1"),
                        "sikkerhet" to mapOf("type" to "string", "enum" to listOf("høy", "middels", "lav")),
                        "bokser" to mapOf(
                            "type" to "array",
                            "description" to if (format == Boksformat.PIKSLER) {
                                "Hvor varen er i bildet, i piksler"
                            } else {
                                "Hvor varen er i bildet, normaliserte koordinater 0–1000"
                            },
                            "items" to BOKS_SKJEMA.getValue(format),
                        ),
                    ),
                ),
            ),
        ),
    )

    val SKJEMA = skjema(Boksformat.HJORNER)

    /** Øvre grense for antall av én vare; høyere tall er nesten alltid feiltolking. */
    const val MAKS_ANTALL = 99

    /** Øvre grense for antall bokser per vare, så et feilaktig svar ikke fyller bildet. */
    const val MAKS_BOKSER = 24

    /**
     * Tolker svaret fra modellen og rydder i det. Ugyldige oppføringer hoppes over
     * i stedet for at hele svaret forkastes, siden resten som regel er nyttig.
     *
     * @param piksler bildets størrelse når boksene er i piksler ([Boksformat.PIKSLER]);
     *   de regnes da om til 0–1000. Null når de allerede er 0–1000.
     * @throws IllegalArgumentException hvis svaret ikke er JSON med en `varer`-liste
     */
    fun tolk(json: String, mapper: JsonMapper, piksler: Bildestorrelse? = null): List<FunnetVare> {
        val rot = try {
            mapper.readTree(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Svaret er ikke gyldig JSON", e)
        }
        val varer = rot.get("varer")
        require(varer != null && varer.isArray) { "Svaret mangler «varer»-liste" }
        return varer.mapNotNull { tilVare(it, piksler) }
    }

    private fun tilVare(node: JsonNode, piksler: Bildestorrelse?): FunnetVare? {
        val navn = node.get("navn")?.takeIf { it.isString }?.asString()?.trim()?.lowercase()
        if (navn.isNullOrEmpty()) return null
        val antall = node.get("antall")?.takeIf { it.isNumber }?.asInt() ?: 1
        val sikkerhet = Sikkerhet.entries.firstOrNull { it.verdi == node.get("sikkerhet")?.asString() }
            ?: Sikkerhet.LAV
        val bokser = node.get("bokser")?.takeIf { it.isArray }
            ?.mapNotNull { tilBoks(it, piksler) }?.take(MAKS_BOKSER)
            .orEmpty()
        return FunnetVare(navn, antall.coerceIn(1, MAKS_ANTALL), sikkerhet, bokser)
    }

    /**
     * Leser en boks i et av formatene, regner piksler om til 0–1000, klemmer koordinatene inn i 0–1000 og
     * sorterer hjørnene, så en boks med byttede hjørner fortsatt blir riktig.
     * Bokser uten areal droppes; varen beholdes.
     */
    private fun tilBoks(node: JsonNode, piksler: Bildestorrelse?): Boks? {
        val raa = if (node.isArray) {
            // box_2d: [ymin, xmin, ymax, xmax]
            if (node.size() != 4 || !node.all { it.isNumber }) return null
            listOf(node[1], node[0], node[3], node[2]).map { it.asInt() }
        } else {
            listOf("x1", "y1", "x2", "y2").map { node.get(it)?.takeIf { n -> n.isNumber }?.asInt() ?: return null }
        }
        val hjorner = if (piksler == null) raa else raa.mapIndexed { i, v ->
            val storrelse = if (i % 2 == 0) piksler.bredde else piksler.hoyde
            Math.round(v * Boks.MAKS.toDouble() / storrelse).toInt()
        }
        val (xa, ya, xb, yb) = hjorner.map { it.coerceIn(0, Boks.MAKS) }
        val boks = Boks(minOf(xa, xb), minOf(ya, yb), maxOf(xa, xb), maxOf(ya, yb))
        return boks.takeIf { it.x2 > it.x1 && it.y2 > it.y1 }
    }
}
