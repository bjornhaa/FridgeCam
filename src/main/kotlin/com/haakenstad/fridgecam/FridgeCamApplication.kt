package com.haakenstad.fridgecam

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

// All innlogging går via JWT (innlogging/SecurityConfig). Uten eksklusjonen lager
// Spring Boot en ubrukt «user»-konto med tilfeldig passord og logger det ved oppstart.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
class FridgeCamApplication

fun main(args: Array<String>) {
    runApplication<FridgeCamApplication>(*args)
}
