package br.com.ajadigital.core.curriculum

import br.com.ajadigital.core.curriculum.application.contracts.ListSubjectsQuery
import br.com.ajadigital.core.curriculum.application.models.ReorderModulesCommand
import br.com.ajadigital.core.curriculum.application.models.UpdateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.RecordSubjectVisitCommand
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectsInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.ReorderModulesInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.UpdateModuleInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.VisitSubjectInputPort
import br.com.ajadigital.core.curriculum.application.usecases.ListSubjectsUseCase
import br.com.ajadigital.core.curriculum.application.usecases.ReorderModulesUseCase
import br.com.ajadigital.core.curriculum.application.usecases.UpdateModuleUseCase
import br.com.ajadigital.core.curriculum.application.usecases.VisitSubjectUseCase
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumConflict
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumForbidden
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumNotFound
import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.FieldChange
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class CurriculumApplicationTests {
    private val store = InMemoryCurriculumStore()
    private val transaction = object : TransactionRunnerOutputPort {
        override fun <T> run(block: () -> T): T = block()
    }
    private val teacher = AuthenticatedActor("teacher", "teacher", true, Instant.EPOCH, "teacher-session")
    private val student = AuthenticatedActor("student", "student", true, Instant.EPOCH, "student-session")
    private val unconfirmed = AuthenticatedActor("student", "student", true, null, "student-session")

    @Test
    fun `list authorization precedes pagination and only readers can query the store`() {
        val port: ListSubjectsInputPort = ListSubjectsUseCase(store)
        assertThrows<CurriculumForbidden> { port.subjects(ListSubjectsQuery(unconfirmed, 0, 0, "id", null, null)) }
        assertThrows<IllegalArgumentException> { port.subjects(ListSubjectsQuery(teacher, 1, 101, "id", null, null)) }
        assertEquals(1, port.subjects(ListSubjectsQuery(teacher, 1, 1, "id", null, "  Test  ")).total)
    }

    @Test
    fun `module editing checks role and rejects invalid transitions before writing`() {
        val port: UpdateModuleInputPort = UpdateModuleUseCase(store, transaction)
        val command = UpdateModuleCommand(student, "module-a", 0, FieldChange.Set(""), FieldChange.Unchanged)
        assertThrows<CurriculumForbidden> { port.updateModule(command) }
        assertThrows<IllegalArgumentException> { port.updateModule(command.copy(actor = teacher)) }
        assertEquals(1, assertThrows<CurriculumConflict> {
            port.updateModule(command.copy(actor = teacher, revision = 2, title = FieldChange.Set("New title")))
        }.currentRevision)
        assertThrows<CurriculumNotFound> {
            port.updateModule(command.copy(actor = teacher, id = "absent", revision = 1, title = FieldChange.Set("New title")))
        }
        val updated = port.updateModule(command.copy(actor = teacher, revision = 1, title = FieldChange.Set("  New title  ")))
        assertEquals("New title", updated.title)
        assertEquals("Original description", updated.description)
    }

    @Test
    fun `reorder and visit commands snapshot collections and reject invalid state`() {
        val reorder: ReorderModulesInputPort = ReorderModulesUseCase(store, transaction)
        val visit: VisitSubjectInputPort = VisitSubjectUseCase(store, transaction)
        val reversed = mutableListOf(ModuleRevision("module-b", 1), ModuleRevision("module-a", 1))
        val command = ReorderModulesCommand(teacher, "subject", 1, reversed, 2, 1)
        reversed.clear()
        assertThrows<CurriculumForbidden> { reorder.reorder(ReorderModulesCommand(student, "subject", 0, emptyList(), 0, 0)) }
        assertThrows<IllegalArgumentException> {
            reorder.reorder(ReorderModulesCommand(teacher, "subject", 1, List(2) { ModuleRevision("module-a", 1) }, 1, 2))
        }
        val page = reorder.reorder(command)
        assertEquals(2, page.total)
        assertEquals("module-a", page.items.single().id)

        val ids = mutableListOf("material:observed")
        val record = RecordSubjectVisitCommand(teacher, "subject", ids)
        ids.add("notice:later")
        assertThrows<CurriculumForbidden> { visit.recordVisit(RecordSubjectVisitCommand(unconfirmed, "subject", listOf("bad"))) }
        assertThrows<IllegalArgumentException> { visit.recordVisit(RecordSubjectVisitCommand(teacher, "subject", listOf("bad"))) }
        assertEquals(listOf("material:observed"), visit.recordVisit(record).seenPublicationIds)
        assertEquals(true, visit.readVisit(teacher, "subject").hasUpdates)
    }

    @Test
    fun `memory store honors the critical curriculum port contract`() {
        assertCurriculumStoreContract(store, "teacher", "teacher-session", "outsider", "outsider-session", "subject", "module-a", "module-b")
    }
}
