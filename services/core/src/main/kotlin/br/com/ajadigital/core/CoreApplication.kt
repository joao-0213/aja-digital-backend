package br.com.ajadigital.core

import br.com.ajadigital.core.application.health.CheckServiceHealth
import br.com.ajadigital.core.ports.out.HealthProbe
import br.com.ajadigital.core.application.identity.OutboxEncryption
import br.com.ajadigital.core.application.identity.RegisterStudent
import br.com.ajadigital.core.application.identity.AuthenticateStudent
import br.com.ajadigital.core.application.identity.RequestAuthToken
import br.com.ajadigital.core.application.identity.ResetPassword
import br.com.ajadigital.core.application.identity.UpdateOwnProfile
import br.com.ajadigital.core.application.academic.ManageAcademics
import br.com.ajadigital.core.application.academic.ManageSubjects
import br.com.ajadigital.core.application.academic.ManageContent
import br.com.ajadigital.core.application.academic.ManageAssessments
import br.com.ajadigital.core.ports.out.AssessmentStore
import br.com.ajadigital.core.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.ports.out.SubjectStore
import br.com.ajadigital.core.ports.out.ContentStore
import br.com.ajadigital.core.ports.out.IdentityStore
import br.com.ajadigital.core.ports.out.PasswordHasher
import br.com.ajadigital.core.ports.out.AcademicStore
import br.com.ajadigital.core.ports.out.IdempotencyStore
import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock

@SpringBootApplication
@EnableScheduling
class CoreApplication {
    @Bean
    fun checkServiceHealth(probe: HealthProbe) = CheckServiceHealth(probe)

    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun outboxEncryption(@Value("\${core.outbox.encryption-key}") key: String) = OutboxEncryption(key)

    @Bean
    fun registerStudent(store: IdentityStore, hasher: PasswordHasher, mapper: ObjectMapper, clock: Clock, encryption: OutboxEncryption) =
        RegisterStudent(store, hasher, mapper, clock, encryption)

    @Bean
    fun authenticateStudent(store: IdentityStore, hasher: PasswordHasher, clock: Clock) =
        AuthenticateStudent(store, hasher, clock)

    @Bean
    fun requestAuthToken(store: IdentityStore, mapper: ObjectMapper, clock: Clock, encryption: OutboxEncryption) =
        RequestAuthToken(store, mapper, clock, encryption)

    @Bean
    fun resetPassword(store: IdentityStore, hasher: PasswordHasher) =
        ResetPassword(store, hasher)

    @Bean
    fun updateOwnProfile(store: IdentityStore) = UpdateOwnProfile(store)

    @Bean
    fun manageAcademics(store: AcademicStore, mapper: ObjectMapper, clock: Clock, encryption: OutboxEncryption, hasher: PasswordHasher, idempotency: IdempotencyStore) =
        ManageAcademics(store, mapper, clock, encryption, hasher, idempotency)

    @Bean
    fun manageSubjects(store: SubjectStore, mapper: ObjectMapper, idempotency: IdempotencyStore) = ManageSubjects(store, mapper, idempotency)

    @Bean
    fun manageContent(store: ContentStore, idempotency: IdempotencyStore, mapper: ObjectMapper) = ManageContent(store, idempotency, mapper)

    @Bean
    fun manageAssessments(store: AssessmentStore, snapshots: QuestionSnapshotProvider, idempotency: IdempotencyStore, mapper: ObjectMapper) = ManageAssessments(store, snapshots, idempotency, mapper)

}

fun main(args: Array<String>) {
    runApplication<CoreApplication>(*args)
}
