package com.haakenstad.fridgecam.innlogging

import com.haakenstad.fridgecam.husstand.Rolle
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegistrerForesporsel(
    @field:NotBlank(message = "Skriv inn e-postadressen din.")
    @field:Email(message = "E-postadressen ser ikke riktig ut.")
    @field:Size(max = 254, message = "E-postadressen er for lang.")
    val epost: String = "",
    @field:NotBlank(message = "Skriv inn navnet ditt.")
    @field:Size(max = 100, message = "Navnet er for langt.")
    val navn: String = "",
    // BCrypt bruker bare de første 72 bytene; InnloggingService sjekker bytene.
    @field:Size(min = 8, max = 72, message = "Passordet må ha mellom 8 og 72 tegn.")
    val passord: String = "",
)

data class LoggInnForesporsel(
    @field:NotBlank(message = "Skriv inn e-postadressen din.")
    val epost: String = "",
    @field:NotBlank(message = "Skriv inn passordet ditt.")
    val passord: String = "",
)

data class InnloggingSvar(
    val token: String,
    val bruker: MegSvar,
)

data class MegSvar(
    val id: Long,
    val epost: String,
    val navn: String,
    val husstander: List<HusstandSvar>,
)

data class HusstandSvar(
    val id: Long,
    val navn: String,
    val rolle: Rolle,
)
