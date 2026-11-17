package com.haakenstad.fridgecam.beholdning

import com.haakenstad.fridgecam.analyse.Boks
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.SqlParameterSource
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

data class NyBeholdningVare(
    val navn: String,
    val antall: Int,
    /** Fra AI-en; tom for varer brukeren la til selv. */
    val bokser: List<Boks> = emptyList(),
)

data class BeholdningVare(
    val id: Long,
    val skanningId: Long?,
    val navn: String,
    val antall: Int,
    val bokser: List<Boks>,
)

@Repository
class BeholdningRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val json: JsonMapper,
) {

    /**
     * Erstatter hele husstandens beholdning med [varer] fra skanningen.
     * Bildet er et øyeblikksbilde av kjøleskapet, så det som ikke er med, er borte.
     */
    @Transactional
    fun erstatt(husstandId: Long, skanningId: Long, varer: List<NyBeholdningVare>) {
        jdbc.update("DELETE FROM beholdning_vare WHERE husstand_id = :h", mapOf("h" to husstandId))
        if (varer.isEmpty()) return
        val rader: Array<SqlParameterSource> = varer.map { vare ->
            MapSqlParameterSource()
                .addValue("h", husstandId)
                .addValue("s", skanningId)
                .addValue("navn", vare.navn)
                .addValue("antall", vare.antall)
                .addValue("bokser", json.writeValueAsString(vare.bokser))
        }.toTypedArray()
        jdbc.batchUpdate(
            """
            INSERT INTO beholdning_vare (husstand_id, skanning_id, navn, antall, bokser)
            VALUES (:h, :s, :navn, :antall, CAST(:bokser AS jsonb))
            """.trimIndent(),
            rader,
        )
    }

    /** Husstandens varer i samme rekkefølge som de ble lagret. */
    fun forHusstand(husstandId: Long): List<BeholdningVare> =
        jdbc.query(
            "SELECT * FROM beholdning_vare WHERE husstand_id = :h ORDER BY id",
            mapOf("h" to husstandId),
        ) { rs, _ ->
            BeholdningVare(
                id = rs.getLong("id"),
                skanningId = rs.getLong("skanning_id").takeUnless { rs.wasNull() },
                navn = rs.getString("navn"),
                antall = rs.getInt("antall"),
                bokser = json.readValue<List<Boks>>(rs.getString("bokser")),
            )
        }
}
