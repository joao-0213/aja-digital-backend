package br.com.ajadigital.core.assessment.application.ports.out

import br.com.ajadigital.core.assessment.application.models.PendingFeedbackCountQuery
import br.com.ajadigital.core.assessment.application.models.SubmissionCreateRecord
import br.com.ajadigital.core.assessment.application.models.SubmissionGetQuery
import br.com.ajadigital.core.assessment.application.models.SubmissionPublishFeedbackRecord
import br.com.ajadigital.core.assessment.application.models.SubmissionSaveFeedbackRecord
import br.com.ajadigital.core.assessment.application.models.SubmissionSubmitRecord
import br.com.ajadigital.core.assessment.application.models.SubmissionUpdateRecord
import br.com.ajadigital.core.assessment.application.models.SubmissionsListQuery
import br.com.ajadigital.core.assessment.domain.models.SubmissionPage
import br.com.ajadigital.core.assessment.domain.models.SubmissionView

interface SubmissionStoreOutputPort {
    fun create(request: SubmissionCreateRecord): SubmissionView
    fun submissions(request: SubmissionsListQuery): SubmissionPage
    fun pendingFeedbackCount(request: PendingFeedbackCountQuery): Int
    fun submission(request: SubmissionGetQuery): SubmissionView?
    fun update(request: SubmissionUpdateRecord): SubmissionView?
    fun submit(request: SubmissionSubmitRecord): SubmissionView?
    fun saveFeedback(request: SubmissionSaveFeedbackRecord): SubmissionView?
    fun publishFeedback(request: SubmissionPublishFeedbackRecord): SubmissionView?
}
