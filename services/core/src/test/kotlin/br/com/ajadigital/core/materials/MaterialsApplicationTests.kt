package br.com.ajadigital.core.materials

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.adapters.out.serialization.JacksonMaterialPublicationReceipt
import br.com.ajadigital.core.materials.application.contracts.MaterialKind
import br.com.ajadigital.core.materials.application.models.MaterialDraft
import br.com.ajadigital.core.materials.application.models.PublishMaterialsCommand
import br.com.ajadigital.core.materials.application.ports.`in`.PublishMaterialsInputPort
import br.com.ajadigital.core.materials.application.usecases.PublishMaterialsUseCase
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsForbidden
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsIdempotencyConflict
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MaterialsApplicationTests {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val teacher = AuthenticatedActor("teacher", "teacher", true, now, "synthetic-session-marker")
    private val other = AuthenticatedActor("other", "teacher", true, now, "synthetic-session-marker")
    private val moduleId = "module"

    @Test
    fun `in memory stores meet the same observable contracts as JDBC`() {
        MaterialsPortContract.stagedFile(TestStagedFileStore(teacher.id, moduleId, Clock.fixed(now, ZoneOffset.UTC)), teacher, other, moduleId)
        MaterialsPortContract.immutableQuestionImage(TestQuestionImageStore(), "file-image")
        MaterialsPortContract.materialWrites(TestLessonStore(teacher.id, moduleId, now), teacher, other, moduleId)
    }

    @Test
    fun `publish input port rejects wrong actor and invalid source then publishes and replays valid material`() {
        val student = AuthenticatedActor("student", "student", true, now, "synthetic-session-marker")
        val store = TestLessonStore(teacher.id, moduleId, now)
        val idempotency = object : EncodedIdempotencyStoreOutputPort {
            private var prior: Pair<FindIdempotencyDto, String>? = null
            override fun findEncoded(request: FindIdempotencyDto): String? = prior?.let { (key, response) ->
                if (request.requestHash != key.requestHash) throw IdempotencyKeyReuse()
                response
            }

            override fun saveEncoded(request: FindIdempotencyDto, response: String) {
                prior = request to response
            }
        }
        val runner = object : TransactionRunnerOutputPort {
            override fun <T> run(request: TransactionRunDto<T>): T = request.block()
        }
        val publisher: PublishMaterialsInputPort = PublishMaterialsUseCase(store, idempotency, JacksonMaterialPublicationReceipt(JsonMapper.builder().build()), runner)
        val lesson = MaterialDraft(moduleId, "Lesson", MaterialKind.LESSON, 10.0, "Body", null, null)
        fun command(actor: AuthenticatedActor, draft: MaterialDraft) = PublishMaterialsCommand(actor, "publish-contract", moduleId, 1, listOf(draft))
        assertThrows(MaterialsForbidden::class.java) { publisher.publish(command(student, lesson)) }
        assertThrows(IllegalArgumentException::class.java) { publisher.publish(command(teacher, lesson.copy(kind = MaterialKind.VIDEO))) }
        val published = publisher.publish(command(teacher, lesson))
        assertEquals("Lesson", published.items.single().title)
        assertEquals(published.items, publisher.publish(command(teacher, lesson)).items)
        assertThrows(MaterialsIdempotencyConflict::class.java) { publisher.publish(command(teacher, lesson.copy(duration = 11.0))) }
    }
}
