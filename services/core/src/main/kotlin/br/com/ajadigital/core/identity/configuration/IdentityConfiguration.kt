package br.com.ajadigital.core.identity.configuration

import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.identity.application.ports.`in`.LoginInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.LogoutInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveSessionInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.RegisterStudentInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.RequestAuthTokenInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResetPasswordInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.UpdateOwnProfileInputPort
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import br.com.ajadigital.core.identity.application.usecases.LoginUseCase
import br.com.ajadigital.core.identity.application.usecases.LogoutUseCase
import br.com.ajadigital.core.identity.application.usecases.ResolveSessionUseCase
import br.com.ajadigital.core.identity.application.usecases.ResolveAuthenticatedActorUseCase
import br.com.ajadigital.core.identity.application.usecases.RegisterStudentUseCase
import br.com.ajadigital.core.identity.application.usecases.RequestAuthTokenUseCase
import br.com.ajadigital.core.identity.application.usecases.ResetPasswordUseCase
import br.com.ajadigital.core.identity.application.usecases.UpdateOwnProfileUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import java.time.Clock

@Configuration
class IdentityConfiguration {
    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun registerStudent(
        store: IdentityStoreOutputPort,
        hasher: PasswordHasherOutputPort,
        mapper: JsonSerializerOutputPort,
        clock: Clock,
        encryption: OutboxPayloadProtectorOutputPort,
    ): RegisterStudentInputPort = RegisterStudentUseCase(store, hasher, mapper, clock, encryption)

    @Bean
    fun login(
        store: IdentityStoreOutputPort,
        hasher: PasswordHasherOutputPort,
        clock: Clock,
    ): LoginInputPort = LoginUseCase(store, hasher, clock)

    @Bean
    fun resolveSession(store: IdentityStoreOutputPort, clock: Clock): ResolveSessionInputPort = ResolveSessionUseCase(store, clock)

    @Bean
    fun resolveAuthenticatedActor(resolveSession: ResolveSessionInputPort): ResolveAuthenticatedActorInputPort =
        ResolveAuthenticatedActorUseCase(resolveSession)

    @Bean
    fun logout(store: IdentityStoreOutputPort): LogoutInputPort = LogoutUseCase(store)

    @Bean
    fun requestAuthToken(
        store: IdentityStoreOutputPort,
        mapper: JsonSerializerOutputPort,
        clock: Clock,
        encryption: OutboxPayloadProtectorOutputPort,
    ): RequestAuthTokenInputPort = RequestAuthTokenUseCase(store, mapper, clock, encryption)

    @Bean
    fun resetPassword(
        store: IdentityStoreOutputPort,
        hasher: PasswordHasherOutputPort,
    ): ResetPasswordInputPort = ResetPasswordUseCase(store, hasher)

    @Bean
    fun updateOwnProfile(store: IdentityStoreOutputPort): UpdateOwnProfileInputPort = UpdateOwnProfileUseCase(store)
}
