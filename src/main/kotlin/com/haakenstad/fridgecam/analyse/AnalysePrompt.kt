package com.haakenstad.fridgecam.analyse

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Prompt, JSON-skjema og tolking av svaret, felles for alle AI-leverandører.
 * Da gir Anthropic og Gemini svar i samme form, og valideringen skjer ett sted.
 */
object AnalysePrompt {

    val SYSTEM = """
        Du ser på et bilde av innholdet i et kjøleskap og lister matvarene du ser.

        Regler:
        - Bruk vanlige norske varenavn i entall og små bokstaver, f.eks. «melk», «egg», «gulost», «paprika».
        - Ta med merke eller variant bare når det skiller varer fra hverandre, f.eks. «lettmelk» og «helmelk».
        - Antall er antall forpakninger eller enheter du faktisk ser, ikke innholdet i dem.
          En eggekartong med synlige egg teller som antall egg; en lukket kartong teller som 1.
        - Sikkerhet:
          - «høy»: varen er tydelig og du er sikker på hva den er.
          - «middels»: du ser varen, men navn eller antall er usikkert.
          - «lav»: varen er delvis skjult eller utydelig.
        - Ikke gjett på ting du ikke ser. Ugjennomsiktige bokser og matbokser uten synlig innhold
          tas med som «matboks» med lav sikkerhet.
        - Ikke ta med selve kjøleskapet, hyller, skuffer eller emballasje uten innhold.
        - Ser du ingen matvarer, returner en tom liste.
    """.trimIndent()

    const val BRUKER = "List matvarene i kjøleskapet på bildet."

    /** JSON-skjema for svaret. Holdt enkelt så alle leverandørene støtter det. */
    val SKJEMA: Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("varer"),
        "properties" to mapOf(
            "varer" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("navn", "antall", "sikkerhet"),
                    "properties" to mapOf(
                        "navn" to mapOf("type" to "string", "description" to "Norsk varenavn i entall"),
                        "antall" to mapOf("type" to "integer", "description" to "Antall synlige enheter, minst 1"),
                        "sikkerhet" to mapOf("type" to "string", "enum" to listOf("høy", "middels", "lav")),
                    ),
                ),
            ),
        ),
    )

    /** Øvre grense for antall av én vare; høyere tall er nesten alltid feiltolking. */
    const val MAKS_ANTALL = 99

    /**
     * Tolker svaret fra modellen og rydder i det. Ugyldige oppføringer hoppes over
     * i stedet for at hele svaret forkastes, siden resten som regel er nyttig.
     *
     * @throws IllegalArgumentException hvis svaret ikke er JSON med en `varer`-liste
     */
    fun tolk(json: String, mapper: JsonMapper): List<FunnetVare> {
        val rot = try {
            mapper.readTree(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Svaret er ikke gyldig JSON", e)
        }
        val varer = rot.get("varer")
        require(varer != null && varer.isArray) { "Svaret mangler «varer»-liste" }
        return varer.mapNotNull(::tilVare)
    }

    private fun tilVare(node: JsonNode): FunnetVare? {
        val navn = node.get("navn")?.takeIf { it.isString }?.asString()?.trim()?.lowercase()
        if (navn.isNullOrEmpty()) return null
        val antall = node.get("antall")?.takeIf { it.isNumber }?.asInt() ?: 1
        val sikkerhet = Sikkerhet.entries.firstOrNull { it.verdi == node.get("sikkerhet")?.asString() }
            ?: Sikkerhet.LAV
        return FunnetVare(navn, antall.coerceIn(1, MAKS_ANTALL), sikkerhet)
    }
}
