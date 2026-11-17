package br.com.ajadigital.core.assessment

import br.com.ajadigital.core.assessment.domain.models.*
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStoreOutputPort
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.assessment.domain.usecases.UpdateAssessmentUseCase
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.adapters.out.serialization.JacksonJsonSerializer
import br.com.ajadigital.core.shared.domain.models.*
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class UpdateAssessmentUseCaseTests {
    private val mapper = JsonMapper.builder().build()
    private val teacher = AcademicActor("teacher-1", "teacher", true, Instant.parse("2026-01-01T00:00:00Z"), "session-hash")

    @Test
    fun `confirmResetDrafts absence false and true are accepted and mapped`() {
        assertEquals(false, update(emptyMap()).confirmResetDrafts)
        assertEquals(false, update(mapOf("confirmResetDrafts" to false)).confirmResetDrafts)
        assertEquals(true, update(mapOf("confirmResetDrafts" to true)).confirmResetDrafts)
    }

    @Test
    fun `confirmResetDrafts explicit null and non-boolean values are rejected`() {
        listOf(null, "true", 1, emptyList<Any>(), emptyMap<String, Any>()).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { update(mapOf("confirmResetDrafts" to value)) }
        }
    }

    @Test
    fun `authorization precedes malformed confirmation value validation`() {
        val forbidden = useCase()
        val student = teacher.copy(role = "student")
        assertThrows(AcademicForbidden::class.java) {
            forbidden.update(UpdateAssessmentDto(student, "assessment-1", "idempotency-123", body(mapOf("confirmResetDrafts" to null))))
        }
    }

    private fun update(fields: Map<String, Any?>): AssessmentStoreUpdateDto {
        val store = RecordingStore()
        val useCase = UpdateAssessmentUseCase(store, noSnapshots, noIdempotency, JacksonJsonSerializer(mapper))
        useCase.update(UpdateAssessmentDto(teacher, "assessment-1", "idempotency-123", body(fields)))
        return checkNotNull(store.updated)
    }

    private fun body(fields: Map<String, Any?>): Any = linkedMapOf<String, Any?>("expectedRevision" to 1).apply { putAll(fields) }

    private fun useCase() = UpdateAssessmentUseCase(RecordingStore(), noSnapshots, noIdempotency, JacksonJsonSerializer(mapper))

    private val noSnapshots = object : QuestionSnapshotProviderOutputPort {
        override fun snapshots(request: QuestionSnapshotProviderSnapshotsDto): List<BankQuestionSnapshot> = error("No question snapshots expected")
    }

    private val noIdempotency = object : IdempotencyStoreOutputPort {
        override fun find(request: FindIdempotencyDto): Map<String, Any?>? = null
        override fun save(request: SaveIdempotencyDto) = Unit
    }

    private class RecordingStore : AssessmentStoreOutputPort {
        var updated: AssessmentStoreUpdateDto? = null
        override fun authorizeWrite(request: AssessmentStoreAuthorizeWriteDto) = Unit
        override fun create(request: AssessmentStoreCreateDto): Map<String, Any?> = error("Not used")
        override fun update(request: AssessmentStoreUpdateDto): Map<String, Any?>? {
            updated = request
            return mapOf("id" to request.id)
        }
        override fun publish(request: AssessmentStorePublishDto): Map<String, Any?>? = error("Not used")
        override fun assessment(request: AssessmentStoreAssessmentDto): Map<String, Any?> = mapOf(
            "id" to request.id, "revision" to 1, "moduleId" to "module-1", "title" to "Work",
            "description" to "", "kind" to "work", "maxGrade" to null, "deadline" to null,
            "required" to false, "allowLate" to false, "questionIds" to emptyList<String>(),
        )
        override fun assessments(request: AssessmentStoreAssessmentsDto): AssessmentPage = error("Not used")
        override fun dashboardAssessments(request: AssessmentStoreDashboardAssessmentsDto): AssessmentPage = error("Not used")
        override fun pendingStudentCount(request: AssessmentStorePendingStudentCountDto): Int = error("Not used")
    }
}
