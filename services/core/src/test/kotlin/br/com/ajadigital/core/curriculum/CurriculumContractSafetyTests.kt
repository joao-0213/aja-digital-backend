package br.com.ajadigital.core.curriculum

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary
import br.com.ajadigital.core.curriculum.application.contracts.SubjectTeacher
import br.com.ajadigital.core.curriculum.application.models.SubjectVisit
import br.com.ajadigital.core.curriculum.application.models.store.StoreAuthorizeSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreCreateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreCreateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreReorderModulesCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreUpdateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreUpdateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreVisitSubjectCommand
import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class CurriculumContractSafetyTests {
    @Test
    fun `write and visit command descriptions redact the session reference`() {
        val marker = "fictitious-session-reference"
        val commands = listOf(
            StoreAuthorizeSubjectCommand("teacher", "teacher", marker, "subject"),
            StoreCreateModuleCommand("teacher", "teacher", marker, "subject", "New module", ""),
            StoreCreateSubjectCommand("admin", marker, "class", "New subject", emptyList(), "blue", "book", ""),
            StoreReorderModulesCommand("subject", "teacher", "teacher", marker, 1, listOf(ModuleRevision("module", 1)), 1, 20),
            StoreUpdateModuleCommand("module", "teacher", "teacher", marker, 1, "Module", null),
            StoreUpdateSubjectCommand("subject", "admin", marker, 1, null, "Subject", null, null, null, null),
            StoreVisitSubjectCommand("subject", "teacher", "teacher", marker, listOf("material:observed")),
        )
        commands.forEach { command ->
            assertFalse(marker in command.toString(), "${command.javaClass.simpleName} exposed the session reference")
        }
    }

    @Test
    fun `subject projections snapshot and protect nested teacher and publication lists`() {
        val teacherIds = mutableListOf("teacher-a", "teacher-b")
        val teachers = mutableListOf(SubjectTeacher("teacher-a", "A"), SubjectTeacher("teacher-b", "B"))
        val publications = mutableListOf("material:observed", "notice:later")
        val seen = mutableListOf("material:observed")
        val timestamp = Instant.EPOCH.toString()
        val subject = Subject("subject", 1, timestamp, timestamp, "admin", "class", "Subject", teacherIds, "blue", "book", "")
        val summary = SubjectSummary(subject, "Class", teachers, true, publications, true)
        val visit = SubjectVisit("subject", timestamp, seen, publications, true)

        teacherIds.clear()
        teachers.clear()
        publications.clear()
        seen.clear()

        assertEquals(listOf("teacher-a", "teacher-b"), summary.subject.teacherIds)
        assertEquals(listOf("teacher-a", "teacher-b"), summary.teachers.map(SubjectTeacher::id))
        assertEquals(listOf("material:observed", "notice:later"), summary.publicationIds)
        assertEquals(listOf("material:observed", "notice:later"), visit.publicationIds)
        assertEquals(listOf("material:observed"), visit.seenPublicationIds)
        listOf(summary.subject.teacherIds, summary.teachers, summary.publicationIds, visit.publicationIds, visit.seenPublicationIds)
            .forEach { list -> assertThrows<UnsupportedOperationException> { (list as MutableList<*>).clear() } }
    }
}
