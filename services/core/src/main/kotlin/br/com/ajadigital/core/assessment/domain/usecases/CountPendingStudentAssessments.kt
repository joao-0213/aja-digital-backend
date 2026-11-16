package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.CountPendingStudentAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class CountPendingStudentAssessments(private val store: AssessmentStore) : CountPendingStudentAssessmentsUseCase {
    override fun pendingStudentCount(actor: AcademicActor): Int {
        AssessmentSupport.reader(actor)
        require(actor.role == "student") { throw AcademicForbidden() }
        return store.pendingStudentCount(actor.id, actor.sessionTokenHash)
    }
}
