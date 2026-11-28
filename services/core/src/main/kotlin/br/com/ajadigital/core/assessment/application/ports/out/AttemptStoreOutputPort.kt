package br.com.ajadigital.core.assessment.application.ports.out

import br.com.ajadigital.core.assessment.application.models.AttemptAnswerSaveCommand
import br.com.ajadigital.core.assessment.application.models.AttemptGetQuery
import br.com.ajadigital.core.assessment.application.models.AttemptListQuery
import br.com.ajadigital.core.assessment.application.models.AttemptResultQuery
import br.com.ajadigital.core.assessment.application.models.AttemptStartCommand
import br.com.ajadigital.core.assessment.application.models.AttemptSubmitCommand
import br.com.ajadigital.core.assessment.application.models.PracticeBlockedQuery
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
