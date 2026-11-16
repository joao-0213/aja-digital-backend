package br.com.ajadigital.core

import br.com.ajadigital.core.health.domain.usecases.CheckServiceHealth
import br.com.ajadigital.core.health.domain.ports.out.HealthProbe
import br.com.ajadigital.core.identity.adapters.out.security.OutboxEncryption
import br.com.ajadigital.core.identity.domain.usecases.RegisterStudent
import br.com.ajadigital.core.identity.domain.usecases.AuthenticateStudent
import br.com.ajadigital.core.identity.domain.usecases.RequestAuthToken
import br.com.ajadigital.core.identity.domain.usecases.ResetPassword
import br.com.ajadigital.core.identity.domain.usecases.UpdateOwnProfile
import br.com.ajadigital.core.classroom.domain.usecases.ManageAcademics
import br.com.ajadigital.core.attendance.domain.usecases.ManageAttendance
import br.com.ajadigital.core.curriculum.domain.usecases.ManageSubjects
import br.com.ajadigital.core.materials.domain.usecases.ManageContent
import br.com.ajadigital.core.assessment.domain.usecases.ManageAssessments
import br.com.ajadigital.core.assessment.domain.usecases.ManageAttempts
import br.com.ajadigital.core.assessment.domain.usecases.ManageSubmissions
import br.com.ajadigital.core.announcements.domain.usecases.ManageAnnouncements
import br.com.ajadigital.core.forum.domain.usecases.ManageTopics
import br.com.ajadigital.core.dashboard.domain.usecases.ManageDashboard
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore
import br.com.ajadigital.core.identity.domain.ports.out.IdentityStore
import br.com.ajadigital.core.identity.domain.ports.out.PasswordHasher
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
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

    @Bean
    fun manageAttempts(store: AttemptStore, mapper: ObjectMapper) = ManageAttempts(store, mapper)

    @Bean
    fun manageSubmissions(store: SubmissionStore, mapper: ObjectMapper) = ManageSubmissions(store, mapper)

    @Bean
    fun manageAnnouncements(store: br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore, mapper: ObjectMapper, idempotency: IdempotencyStore) = ManageAnnouncements(store, mapper, idempotency)

    @Bean
    fun manageTopics(store: TopicStore, questions: QuestionSnapshotProvider, idempotency: IdempotencyStore, mapper: ObjectMapper) = ManageTopics(store, questions, idempotency, mapper)

    @Bean
    fun manageDashboard(academics: ManageAcademics, subjects: ManageSubjects, content: ManageContent, assessments: ManageAssessments, submissions: ManageSubmissions, announcements: ManageAnnouncements, topics: ManageTopics, clock: Clock) =
        ManageDashboard(academics, subjects, content, assessments, submissions, announcements, topics, clock)

    @Bean
    fun manageAttendance(store: AttendanceStore, idempotency: IdempotencyStore, mapper: ObjectMapper) = ManageAttendance(store, idempotency, mapper)

}

fun main(args: Array<String>) {
    runApplication<CoreApplication>(*args)
}
