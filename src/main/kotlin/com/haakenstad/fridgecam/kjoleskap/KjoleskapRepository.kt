package com.haakenstad.fridgecam.kjoleskap

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

/** Et kjøleskap i husstanden (#61), f.eks. «Hjemme» eller «Hytta». */
data class Kjoleskap(
    val id: Long,
    val husstandId: Long,
    val navn: String,
    /** Tidspunktet for siste skanning av kjøleskapet, null hvis det aldri er skannet. */
    val sistSkannet: Instant?,
)

@Repository
class KjoleskapRepository(private val jdbc: NamedParameterJdbcTemplate) {

    private val utvalg = """
        SELECT k.id, k.husstand_id, k.navn,
               (SELECT max(s.tidspunkt) FROM skanning s WHERE s.kjoleskap_id = k.id) AS sist_skannet
        FROM kjoleskap k
    """.trimIndent()

    private val rad = RowMapper { rs, _ ->
        Kjoleskap(
            id = rs.getLong("id"),
            husstandId = rs.getLong("husstand_id"),
            navn = rs.getString("navn"),
            sistSkannet = rs.getTimestamp("sist_skannet")?.toInstant(),
        )
    }

    fun opprett(husstandId: Long, navn: String): Long =
        jdbc.queryForObject(
            "INSERT INTO kjoleskap (husstand_id, navn) VALUES (:h, :navn) RETURNING id",
            mapOf("h" to husstandId, "navn" to navn),
            Long::class.java,
        )!!

    /** Husstandens kjøleskap, eldste først; det første er standard når ingen er valgt. */
    fun forHusstand(husstandId: Long): List<Kjoleskap> =
        jdbc.query("$utvalg WHERE k.husstand_id = :h ORDER BY k.opprettet, k.id", mapOf("h" to husstandId), rad)

    fun finn(id: Long): Kjoleskap? =
        jdbc.query("$utvalg WHERE k.id = :id", mapOf("id" to id), rad).firstOrNull()
}
