package com.haakenstad.fridgecam.innlogging

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Stateless JWT som i talik-ai: ingen sesjon, ingen cookies, og derfor ingen CSRF.
 *
 * - Alt under `/api/` krever innlogging, unntatt registrering og innlogging.
 * - `/actuator/health` er åpen (helsesjekk fra Fly).
 * - Alt annet (frontenden, dype lenker, `/error`) er åpent; frontenden sender
 *   brukeren til innlogging før den kaller API-et.
 */
@Configuration
@EnableConfigurationProperties(JwtEgenskaper::class)
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity, jwt: JwtService): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.POST, "/api/auth/registrer", "/api/auth/logg-inn").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/api/**", "/actuator/**").authenticated()
                    .anyRequest().permitAll()
            }
            .exceptionHandling { feil ->
                // Samme feilformat som resten av API-et, og 401 i stedet for Springs 403.
                feil.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpStatus.UNAUTHORIZED.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = "UTF-8"
                    response.writer.write("""{"code":"ikke_innlogget","message":"Du må logge inn."}""")
                }
            }
            .addFilterBefore(JwtFilter(jwt), UsernamePasswordAuthenticationFilter::class.java)
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
        return http.build()
    }
}
