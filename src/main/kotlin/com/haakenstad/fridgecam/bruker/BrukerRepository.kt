package com.haakenstad.fridgecam.bruker

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

data class Bruker(
    val id: Long,
    val epost: String,
    val navn: String,
    val passordHash: String,
)

@Repository
class BrukerRepository(private val jdbc: NamedParameterJdbcTemplate) {

    private val rad = RowMapper { rs, _ ->
        Bruker(
            id = rs.getLong("id"),
            epost = rs.getString("epost"),
            navn = rs.getString("navn"),
            passordHash = rs.getString("passord_hash"),
        )
    }

    fun finn(id: Long): Bruker? =
        jdbc.query("SELECT * FROM bruker WHERE id = :id", mapOf("id" to id), rad).firstOrNull()

    /** Store/små bokstaver spiller ingen rolle (samme uttrykk som den unike indeksen). */
    fun finnMedEpost(epost: String): Bruker? =
        jdbc.query("SELECT * FROM bruker WHERE lower(epost) = lower(:epost)", mapOf("epost" to epost), rad)
            .firstOrNull()

    /** Kaster `DuplicateKeyException` hvis e-posten allerede er i bruk. */
    fun opprett(epost: String, navn: String, passordHash: String): Bruker =
        jdbc.queryForObject(
            """
            INSERT INTO bruker (epost, navn, passord_hash)
            VALUES (:epost, :navn, :passordHash)
            RETURNING *
            """.trimIndent(),
            mapOf("epost" to epost, "navn" to navn, "passordHash" to passordHash),
            rad,
        )!!
}
