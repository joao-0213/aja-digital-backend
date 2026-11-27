package br.com.ajadigital.core.assessment.application.ports.out

import br.com.ajadigital.core.assessment.application.models.*
import br.com.ajadigital.core.assessment.domain.models.AttemptPage
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord
import br.com.ajadigital.core.assessment.domain.models.AttemptResult

interface AttemptStoreOutputPort {
    fun practiceBlocked(request: PracticeBlockedQuery): Boolean
    fun start(request: AttemptStartCommand): AttemptRecord
    fun attempts(request: AttemptListQuery): AttemptPage
    fun attempt(request: AttemptGetQuery): AttemptRecord?
    fun saveAnswers(request: AttemptAnswerSaveCommand): AttemptRecord?
    fun submit(request: AttemptSubmitCommand): AttemptResult?
    fun result(request: AttemptResultQuery): AttemptResult?
}
