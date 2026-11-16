package com.haakenstad.fridgecam.config

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import javax.sql.DataSource

/**
 * Innebygd Postgres for lokal utvikling (samme oppsett som i talik-ai).
 *
 * Basen er flyktig: alt forsvinner når appen stopper, og Flyway bygger
 * skjemaet på nytt ved hver oppstart.
 */
@Configuration
@Profile("local")
class LocalDataSourceConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Porten er låst (se `fridgecam.local-db.port`) slik at en DB-tilkobling i
     * IntelliJ overlever en restart av appen. 0 = la Zonky velge en ledig port,
     * som testene gjør.
     */
    @Bean(destroyMethod = "close")
    fun embeddedPostgres(
        @Value("\${fridgecam.local-db.port:55433}") port: Int,
    ): EmbeddedPostgres {
        // Zonky kaster ikke når porten er opptatt: dens egen Postgres feiler
        // bindingen, men start() returnerer likevel, og DataSource-en peker da
        // på en annen instans sin database. Derfor sjekker vi selv.
        if (port > 0 && portErOpptatt(port)) {
            throw IllegalStateException(
                "Port $port er allerede i bruk — kjører appen et annet sted? " +
                    "Stopp den, sett FRIDGECAM_LOCAL_DB_PORT til en annen port, eller 0 for tilfeldig valg.",
            )
        }
        val builder = EmbeddedPostgres.builder()
        if (port > 0) builder.setPort(port)
        val pg = builder.start()
        log.info("Embedded Postgres: {} (bruker postgres, tomt passord)", pg.getJdbcUrl("postgres", "postgres"))
        return pg
    }

    private fun portErOpptatt(port: Int): Boolean =
        try {
            ServerSocket().use { sock ->
                sock.reuseAddress = false
                sock.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
            }
            false
        } catch (e: IOException) {
            true
        }

    @Bean
    @Primary
    fun dataSource(pg: EmbeddedPostgres): DataSource = pg.postgresDatabase
}
