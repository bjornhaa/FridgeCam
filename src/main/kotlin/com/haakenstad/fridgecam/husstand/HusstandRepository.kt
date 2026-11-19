package com.haakenstad.fridgecam.husstand

import com.haakenstad.fridgecam.analyse.AiLeverandor
import com.haakenstad.fridgecam.analyse.AiValg
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

enum class Rolle { EIER, MEDLEM }

data class Medlemskap(
    val husstandId: Long,
    val navn: String,
    val rolle: Rolle,
)

@Repository
class HusstandRepository(private val jdbc: NamedParameterJdbcTemplate) {

    fun opprett(navn: String): Long =
        jdbc.queryForObject(
            "INSERT INTO husstand (navn) VALUES (:navn) RETURNING id",
            mapOf("navn" to navn),
            Long::class.java,
        )!!

    fun leggTilMedlem(husstandId: Long, brukerId: Long, rolle: Rolle) {
        jdbc.update(
            "INSERT INTO husstand_medlem (husstand_id, bruker_id, rolle) VALUES (:h, :b, :r)",
            mapOf("h" to husstandId, "b" to brukerId, "r" to rolle.name),
        )
    }

    /** Husstandene brukeren er med i, eldste medlemskap først. */
    fun forBruker(brukerId: Long): List<Medlemskap> =
        jdbc.query(
            """
            SELECT h.id, h.navn, m.rolle
            FROM husstand_medlem m
            JOIN husstand h ON h.id = m.husstand_id
            WHERE m.bruker_id = :b
            ORDER BY m.lagt_til, h.id
            """.trimIndent(),
            mapOf("b" to brukerId),
        ) { rs, _ -> Medlemskap(rs.getLong("id"), rs.getString("navn"), Rolle.valueOf(rs.getString("rolle"))) }

    /** Husstandens eget valg av AI-leverandør og modell (#54), eller null for appens standard. */
    fun aiValg(husstandId: Long): AiValg? =
        jdbc.query(
            "SELECT ai_leverandor, ai_modell FROM husstand WHERE id = :h AND ai_leverandor IS NOT NULL",
            mapOf("h" to husstandId),
        ) { rs, _ -> AiValg(AiLeverandor.valueOf(rs.getString("ai_leverandor")), rs.getString("ai_modell")) }
            .firstOrNull()

    fun settAiValg(husstandId: Long, valg: AiValg) {
        jdbc.update(
            "UPDATE husstand SET ai_leverandor = :l, ai_modell = :m WHERE id = :h",
            mapOf("h" to husstandId, "l" to valg.leverandor.name, "m" to valg.modell),
        )
    }
}
