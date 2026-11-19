package com.haakenstad.fridgecam.ainokkel

import com.haakenstad.fridgecam.analyse.AiLeverandor
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Krypterer husstandenes AI-nøkler med AES-256-GCM, som Spotify-tokens i talik-ai.
 *
 * - Hovednøkkelen kommer fra `FRIDGECAM_NOKKEL_KRYPTERING` (base64, 32 byte) og
 *   ligger aldri i databasen. Uten den starter ikke appen.
 * - Hver kryptering får ny, tilfeldig IV, så samme nøkkel gir forskjellig chiffertekst.
 * - Husstand og leverandør er med som AAD: en kryptert verdi kopiert til en annen
 *   husstand (f.eks. med SQL-tilgang) lar seg ikke dekryptere der.
 * - GCM oppdager endringer i chifferteksten; dekryptering feiler da i stedet for å gi søppel.
 *
 * Formatet er `v1:` + base64(IV ‖ chiffertekst ‖ tag), så hovednøkkelen kan byttes
 * senere med en `v2` uten å miste eksisterende nøkler.
 */
@Component
class NokkelKryptering(
    @Value("\${fridgecam.ai.nokkel-kryptering:}") hovednokkel: String,
    env: Environment,
) {

    private val nokkel: SecretKey = lagNokkel(hovednokkel, env.acceptsProfiles(Profiles.of("local")))
    private val random = SecureRandom()

    fun krypter(klartekst: String, husstandId: Long, leverandor: AiLeverandor): String {
        val iv = ByteArray(IV_BYTE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITME)
        cipher.init(Cipher.ENCRYPT_MODE, nokkel, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(husstandId, leverandor))
        val chiffer = cipher.doFinal(klartekst.toByteArray(Charsets.UTF_8))
        return VERSJON + Base64.getEncoder().encodeToString(iv + chiffer)
    }

    /** Kaster hvis verdien er endret, hører til en annen husstand eller er kryptert med en annen hovednøkkel. */
    fun dekrypter(kryptert: String, husstandId: Long, leverandor: AiLeverandor): String {
        check(kryptert.startsWith(VERSJON)) { "Ukjent format på kryptert AI-nøkkel" }
        val data = Base64.getDecoder().decode(kryptert.removePrefix(VERSJON))
        check(data.size > IV_BYTE + TAG_BITS / 8) { "Kryptert AI-nøkkel er for kort" }
        val cipher = Cipher.getInstance(ALGORITME)
        cipher.init(Cipher.DECRYPT_MODE, nokkel, GCMParameterSpec(TAG_BITS, data, 0, IV_BYTE))
        cipher.updateAAD(aad(husstandId, leverandor))
        return String(cipher.doFinal(data, IV_BYTE, data.size - IV_BYTE), Charsets.UTF_8)
    }

    private fun aad(husstandId: Long, leverandor: AiLeverandor) =
        "fridgecam/husstand_ai_nokkel/$husstandId/${leverandor.name}".toByteArray(Charsets.UTF_8)

    companion object {
        private const val ALGORITME = "AES/GCM/NoPadding"
        private const val VERSJON = "v1:"
        private const val NOKKEL_BYTE = 32
        private const val IV_BYTE = 12
        private const val TAG_BITS = 128

        /**
         * Feilmeldingene sier aldri noe om selve verdien. Utviklingsnøkkelen
         * (32 nullbyte, i application-local.yml) avvises utenfor `local`, så prod
         * ikke stille krypterer med en nøkkel som står i et offentlig repo.
         */
        internal fun lagNokkel(base64: String, lokal: Boolean): SecretKey {
            check(base64.isNotBlank()) {
                "FRIDGECAM_NOKKEL_KRYPTERING må settes (base64, 32 byte). Lag en med: openssl rand -base64 32"
            }
            val bytes = try {
                Base64.getDecoder().decode(base64.trim())
            } catch (_: IllegalArgumentException) {
                throw IllegalStateException("FRIDGECAM_NOKKEL_KRYPTERING er ikke gyldig base64.")
            }
            try {
                check(bytes.size == NOKKEL_BYTE) {
                    "FRIDGECAM_NOKKEL_KRYPTERING må være $NOKKEL_BYTE byte (base64), men er ${bytes.size}."
                }
                check(lokal || bytes.any { it != 0.toByte() }) {
                    "FRIDGECAM_NOKKEL_KRYPTERING er utviklingsnøkkelen, som bare kan brukes med local-profilen."
                }
                return SecretKeySpec(bytes, "AES")
            } finally {
                // SecretKeySpec har sin egen kopi.
                bytes.fill(0)
            }
        }
    }
}
