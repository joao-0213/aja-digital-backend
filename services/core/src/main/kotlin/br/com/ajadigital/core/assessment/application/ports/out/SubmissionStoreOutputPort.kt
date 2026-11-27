package br.com.ajadigital.core.assessment.application.ports.out

import br.com.ajadigital.core.assessment.domain.models.*
import br.com.ajadigital.core.assessment.application.models.*

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
