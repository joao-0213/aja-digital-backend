package br.com.ajadigital.core.assessment.domain.ports.out

import br.com.ajadigital.core.assessment.domain.models.*

interface AssessmentStoreOutputPort {
    fun authorizeWrite(request: AssessmentStoreAuthorizeWriteDto): Unit
    fun create(request: AssessmentStoreCreateDto): Map<String, Any?>
    fun update(request: AssessmentStoreUpdateDto): Map<String, Any?>?
    fun publish(request: AssessmentStorePublishDto): Map<String, Any?>?
    fun assessment(request: AssessmentStoreAssessmentDto): Map<String, Any?>?
    fun assessments(request: AssessmentStoreAssessmentsDto): AssessmentPage
    fun dashboardAssessments(request: AssessmentStoreDashboardAssessmentsDto): AssessmentPage
    fun pendingStudentCount(request: AssessmentStorePendingStudentCountDto): Int
}
