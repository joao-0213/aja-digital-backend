package br.com.ajadigital.core.curriculum

import br.com.ajadigital.core.curriculum.application.models.RevisionWrite
import br.com.ajadigital.core.curriculum.application.models.store.StoreReorderModulesCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreSubjectModulesQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreSubjectQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreVisitSubjectCommand
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumForbidden
import br.com.ajadigital.core.curriculum.domain.exceptions.UnknownPublication
import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows

internal fun assertCurriculumStoreContract(
    store: SubjectStoreOutputPort,
    teacherId: String,
    teacherSession: String,
    outsiderId: String,
    outsiderSession: String,
    subjectId: String,
    firstModule: String,
    secondModule: String,
) {
    val subject = StoreSubjectQuery(subjectId, teacherId, "teacher")
    val revision = checkNotNull(store.subject(subject)).subject.revision
    val modules = StoreSubjectModulesQuery(subjectId, teacherId, "teacher")
    assertEquals(listOf(firstModule, secondModule), store.subjectModules(modules).map { it.id })
    assertNull(store.subject(subject.copy(actorId = outsiderId)))
    val order = StoreReorderModulesCommand(
        subjectId, teacherId, "teacher", teacherSession, revision,
        listOf(ModuleRevision(secondModule, 1), ModuleRevision(firstModule, 1)), 2, 1,
    )
    assertThrows<CurriculumForbidden> { store.reorderModules(order.copy(actorId = outsiderId, sessionHash = outsiderSession)) }
    assertEquals(RevisionWrite.Missing, store.reorderModules(order.copy(subjectId = "absent")))
    assertEquals(RevisionWrite.Conflict(revision), store.reorderModules(order.copy(items = order.items.dropLast(1))))
    assertEquals(RevisionWrite.Conflict(revision), store.reorderModules(order.copy(items = order.items.map { it.copy(revision = 2) })))
    assertEquals(listOf(firstModule, secondModule), store.subjectModules(modules).map { it.id })
    val applied = store.reorderModules(order)
    check(applied is RevisionWrite.Applied)
    assertEquals(2, applied.value.total)
    assertEquals(2, applied.value.page)
    assertEquals(firstModule, applied.value.items.single().id)
    assertEquals(2, applied.value.items.single().revision)
    assertEquals(RevisionWrite.Conflict(revision + 1), store.reorderModules(order))

    val visit = StoreVisitSubjectCommand(subjectId, teacherId, "teacher", teacherSession, null)
    val unread = checkNotNull(store.visit(visit))
    assertNull(unread.seenAt)
    assertTrue(unread.hasUpdates)
    assertThrows<UnknownPublication> { store.visit(visit.copy(publicationIds = listOf("notice:unknown"))) }
    assertEquals(emptyList<String>(), checkNotNull(store.visit(visit)).seenPublicationIds)
    val observed = checkNotNull(store.visit(visit.copy(publicationIds = listOf("material:observed"))))
    assertEquals(listOf("material:observed"), observed.seenPublicationIds)
    assertNotNull(observed.seenAt)
    assertTrue(observed.hasUpdates)
    assertEquals(listOf("material:observed", "notice:later"),
        checkNotNull(store.visit(visit.copy(publicationIds = listOf("notice:later")))).seenPublicationIds)
}
