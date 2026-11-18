package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.analyse.AiLeverandor
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

/** En lagret nøkkel. [kryptert] er chifferteksten fra [NokkelKryptering], aldri klartekst. */
class LagretAiNokkel(
    val kryptert: String,
    val sisteTegn: String,
    val endret: Instant,
    /** Navnet på den som la inn nøkkelen, null hvis brukeren er slettet. */
    val endretAv: String?,
) {
    override fun toString() = "LagretAiNokkel(sisteTegn=$sisteTegn, endret=$endret)"
}

@Repository
class AiNokkelRepository(private val jdbc: NamedParameterJdbcTemplate) {

    /** Legger inn eller bytter husstandens nøkkel for leverandøren. */
    fun lagre(husstandId: Long, leverandor: AiLeverandor, kryptert: String, sisteTegn: String, brukerId: Long) {
        jdbc.update(
            """
            INSERT INTO husstand_ai_nokkel (husstand_id, leverandor, nokkel_kryptert, siste_tegn, endret_av)
            VALUES (:h, :l, :k, :s, :b)
            ON CONFLICT (husstand_id, leverandor) DO UPDATE
               SET nokkel_kryptert = excluded.nokkel_kryptert,
                   siste_tegn      = excluded.siste_tegn,
                   endret          = now(),
                   endret_av       = excluded.endret_av
            """.trimIndent(),
            mapOf("h" to husstandId, "l" to leverandor.name, "k" to kryptert, "s" to sisteTegn, "b" to brukerId),
        )
    }

    fun finn(husstandId: Long, leverandor: AiLeverandor): LagretAiNokkel? =
        jdbc.query(
            """
            SELECT n.nokkel_kryptert, n.siste_tegn, n.endret, b.navn
            FROM husstand_ai_nokkel n
            LEFT JOIN bruker b ON b.id = n.endret_av
            WHERE n.husstand_id = :h AND n.leverandor = :l
            """.trimIndent(),
            mapOf("h" to husstandId, "l" to leverandor.name),
        ) { rs, _ ->
            LagretAiNokkel(
                kryptert = rs.getString("nokkel_kryptert"),
                sisteTegn = rs.getString("siste_tegn"),
                endret = rs.getTimestamp("endret").toInstant(),
                endretAv = rs.getString("navn"),
            )
        }.firstOrNull()

    fun slett(husstandId: Long, leverandor: AiLeverandor) {
        jdbc.update(
            "DELETE FROM husstand_ai_nokkel WHERE husstand_id = :h AND leverandor = :l",
            mapOf("h" to husstandId, "l" to leverandor.name),
        )
    }
}
