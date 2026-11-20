package com.haakenstad.fridgecam.skanning

import com.haakenstad.fridgecam.analyse.FunnetVare
import com.haakenstad.fridgecam.analyse.Tokenforbruk
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant

data class NySkanning(
    val husstandId: Long,
    val kjoleskapId: Long,
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
    val kjoleskapId: Long,
    val brukerId: Long?,
    val tidspunkt: Instant,
    val leverandor: String,
    val forbruk: Tokenforbruk?,
    val aiVarer: List<FunnetVare>,
    /** Når den rettede listen ble lagret som beholdning; null hvis aldri. */
    val lagret: Instant? = null,
)

/** En vare slik brukeren rettet den. [aiIndeks] peker på varen i AI-ens liste, null hvis lagt til. */
data class RettetVare(
    val navn: String,
    val antall: Int,
    val aiIndeks: Int?,
)

class Bilde(val mimeType: String, val data: ByteArray)

@Repository
class SkanningRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val json: JsonMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val rad = RowMapper { rs, _ ->
        val modell = rs.getString("modell")
        Skanning(
            id = rs.getLong("id"),
            husstandId = rs.getLong("husstand_id"),
            kjoleskapId = rs.getLong("kjoleskap_id"),
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
            lagret = rs.getTimestamp("lagret")?.toInstant(),
        )
    }

    /**
     * Lagrer skanningen og bildet, og returnerer id-en. Rydder samtidig bort
     * kjøleskapets gamle bilder ([slettGamleBilder]), så ingen egen jobb trengs.
     */
    @Transactional
    fun opprett(skanning: NySkanning): Long {
        val id = jdbc.queryForObject(
            """
            INSERT INTO skanning (husstand_id, kjoleskap_id, bruker_id, leverandor, modell, tokens_inn, tokens_ut, kostnad_usd, ai_varer)
            VALUES (:husstandId, :kjoleskapId, :brukerId, :leverandor, :modell, :tokensInn, :tokensUt, :kostnadUsd, CAST(:aiVarer AS jsonb))
            RETURNING id
            """.trimIndent(),
            mapOf(
                "husstandId" to skanning.husstandId,
                "kjoleskapId" to skanning.kjoleskapId,
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
        slettGamleBilder(skanning.kjoleskapId)
        return id
    }

    /**
     * Bare det siste bildet trengs (#43): Kjøleskapet viser det, og ingen
     * historikk brukes. Per kjøleskap (#61) beholdes bildet for den nyeste
     * skanningen (den brukeren kanskje retter nå) og for skanningen beholdningen
     * kom fra; alle andre slettes. Som regel er det samme skanning, altså ett
     * bilde per kjøleskap. Andre kjøleskap i husstanden røres ikke.
     *
     * Selve skanningene (AI-svar, rettet liste, kostnad) beholdes; de tar lite
     * plass og trengs for å måle AI-kvaliteten. Med åpen registrering kan da
     * ingen fylle databasen med bilder.
     *
     * @return antall slettede bilder
     */
    fun slettGamleBilder(kjoleskapId: Long): Int {
        val slettet = jdbc.update(
            """
            DELETE FROM skanning_bilde b
            USING skanning s
            WHERE b.skanning_id = s.id
              AND s.kjoleskap_id = :k
              AND s.id <> (
                  SELECT id FROM skanning WHERE kjoleskap_id = :k ORDER BY tidspunkt DESC, id DESC LIMIT 1
              )
              AND NOT EXISTS (SELECT 1 FROM beholdning_vare v WHERE v.skanning_id = s.id)
            """.trimIndent(),
            mapOf("k" to kjoleskapId),
        )
        if (slettet > 0) log.info("Slettet {} gamle bilder for kjøleskap {}", slettet, kjoleskapId)
        return slettet
    }

    /** Lagrer brukerens rettede liste på skanningen, til senere måling av AI-kvalitet. */
    fun markerLagret(id: Long, rettede: List<RettetVare>) {
        jdbc.update(
            "UPDATE skanning SET rettede_varer = CAST(:rettede AS jsonb), lagret = now() WHERE id = :id",
            mapOf("id" to id, "rettede" to json.writeValueAsString(rettede)),
        )
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
