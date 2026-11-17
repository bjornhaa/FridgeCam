package com.haakenstad.fridgecam.skanning

import com.haakenstad.fridgecam.analyse.FunnetVare
import com.haakenstad.fridgecam.analyse.Tokenforbruk
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant

data class NySkanning(
    val husstandId: Long,
    val brukerId: Long,
    val leverandor: String,
    val forbruk: Tokenforbruk?,
    val aiVarer: List<FunnetVare>,
    val bilde: Bilde,
)

/** En skanning uten bildedata; bildet hentes for seg med [SkanningRepository.hentBilde]. */
data class Skanning(
    val id: Long,
    val husstandId: Long,
    val brukerId: Long?,
    val tidspunkt: Instant,
    val leverandor: String,
    val forbruk: Tokenforbruk?,
    val aiVarer: List<FunnetVare>,
)

class Bilde(val mimeType: String, val data: ByteArray)

@Repository
class SkanningRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val json: JsonMapper,
) {

    private val rad = RowMapper { rs, _ ->
        val modell = rs.getString("modell")
        Skanning(
            id = rs.getLong("id"),
            husstandId = rs.getLong("husstand_id"),
            brukerId = rs.getLong("bruker_id").takeUnless { rs.wasNull() },
            tidspunkt = rs.getTimestamp("tidspunkt").toInstant(),
            leverandor = rs.getString("leverandor"),
            forbruk = modell?.let {
                Tokenforbruk(
                    modell = it,
                    inn = rs.getLong("tokens_inn"),
                    ut = rs.getLong("tokens_ut"),
                    kostnadUsd = rs.getBigDecimal("kostnad_usd")?.toDouble(),
                )
            },
            aiVarer = json.readValue<List<FunnetVare>>(rs.getString("ai_varer")),
        )
    }

    /** Lagrer skanningen og bildet, og returnerer id-en. */
    @Transactional
    fun opprett(skanning: NySkanning): Long {
        val id = jdbc.queryForObject(
            """
            INSERT INTO skanning (husstand_id, bruker_id, leverandor, modell, tokens_inn, tokens_ut, kostnad_usd, ai_varer)
            VALUES (:husstandId, :brukerId, :leverandor, :modell, :tokensInn, :tokensUt, :kostnadUsd, CAST(:aiVarer AS jsonb))
            RETURNING id
            """.trimIndent(),
            mapOf(
                "husstandId" to skanning.husstandId,
                "brukerId" to skanning.brukerId,
                "leverandor" to skanning.leverandor,
                "modell" to skanning.forbruk?.modell,
                "tokensInn" to skanning.forbruk?.inn,
                "tokensUt" to skanning.forbruk?.ut,
                "kostnadUsd" to skanning.forbruk?.kostnadUsd?.toBigDecimal(),
                "aiVarer" to json.writeValueAsString(skanning.aiVarer),
            ),
            Long::class.java,
        )!!
        jdbc.update(
            "INSERT INTO skanning_bilde (skanning_id, mime_type, data) VALUES (:id, :mimeType, :data)",
            mapOf("id" to id, "mimeType" to skanning.bilde.mimeType, "data" to skanning.bilde.data),
        )
        return id
    }

    fun finn(id: Long): Skanning? =
        jdbc.query("SELECT * FROM skanning WHERE id = :id", mapOf("id" to id), rad).firstOrNull()

    /** Husstandens nyeste skanning, eller null hvis den aldri har skannet. */
    fun siste(husstandId: Long): Skanning? =
        jdbc.query(
            "SELECT * FROM skanning WHERE husstand_id = :h ORDER BY tidspunkt DESC, id DESC LIMIT 1",
            mapOf("h" to husstandId),
            rad,
        ).firstOrNull()

    fun hentBilde(skanningId: Long): Bilde? =
        jdbc.query(
            "SELECT mime_type, data FROM skanning_bilde WHERE skanning_id = :id",
            mapOf("id" to skanningId),
        ) { rs, _ -> Bilde(rs.getString("mime_type"), rs.getBytes("data")) }.firstOrNull()
}
