package br.com.ajadigital.core.assessment.domain.ports.out

import br.com.ajadigital.core.assessment.domain.models.*

interface AttemptStoreOutputPort {
    fun practiceBlocked(request: AttemptStorePracticeBlockedDto): Boolean
    fun start(request: AttemptStoreStartDto): Map<String, Any?>
    fun attempts(request: AttemptStoreAttemptsDto): AssessmentPage
    fun attempt(request: AttemptStoreAttemptDto): Map<String, Any?>?
    fun saveAnswers(request: AttemptStoreSaveAnswersDto): Map<String, Any?>?
    fun submit(request: AttemptStoreSubmitDto): Map<String, Any?>?
    fun result(request: AttemptStoreResultDto): Map<String, Any?>?
}
