package br.com.ajadigital.core.identity.configuration

import br.com.ajadigital.core.outbox.domain.ports.out.OutboxPayloadProtector
import br.com.ajadigital.core.identity.domain.ports.`in`.AuthenticateStudentUseCase
import br.com.ajadigital.core.identity.domain.ports.`in`.RegisterStudentUseCase
import br.com.ajadigital.core.identity.domain.ports.`in`.RequestAuthTokenUseCase
import br.com.ajadigital.core.identity.domain.ports.`in`.ResetPasswordUseCase
import br.com.ajadigital.core.identity.domain.ports.`in`.UpdateOwnProfileUseCase
import br.com.ajadigital.core.identity.domain.ports.out.IdentityStore
import br.com.ajadigital.core.identity.domain.ports.out.PasswordHasher
import br.com.ajadigital.core.identity.domain.usecases.AuthenticateStudent
import br.com.ajadigital.core.identity.domain.usecases.RegisterStudent
import br.com.ajadigital.core.identity.domain.usecases.RequestAuthToken
import br.com.ajadigital.core.identity.domain.usecases.ResetPassword
import br.com.ajadigital.core.identity.domain.usecases.UpdateOwnProfile
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import tools.jackson.databind.ObjectMapper
import java.time.Clock

@Configuration
class IdentityConfiguration {
    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun registerStudent(
        store: IdentityStore,
        hasher: PasswordHasher,
        mapper: ObjectMapper,
        clock: Clock,
        encryption: OutboxPayloadProtector,
    ): RegisterStudentUseCase = RegisterStudent(store, hasher, mapper, clock, encryption)

    @Bean
    fun authenticateStudent(
        store: IdentityStore,
        hasher: PasswordHasher,
        clock: Clock,
    ): AuthenticateStudentUseCase = AuthenticateStudent(store, hasher, clock)

    @Bean
    fun requestAuthToken(
        store: IdentityStore,
        mapper: ObjectMapper,
        clock: Clock,
        encryption: OutboxPayloadProtector,
    ): RequestAuthTokenUseCase = RequestAuthToken(store, mapper, clock, encryption)

    @Bean
    fun resetPassword(
        store: IdentityStore,
        hasher: PasswordHasher,
    ): ResetPasswordUseCase = ResetPassword(store, hasher)

    @Bean
    fun updateOwnProfile(store: IdentityStore): UpdateOwnProfileUseCase = UpdateOwnProfile(store)
}
