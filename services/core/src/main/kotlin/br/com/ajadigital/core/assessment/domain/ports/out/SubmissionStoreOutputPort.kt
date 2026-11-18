package br.com.ajadigital.core.assessment.domain.ports.out

import br.com.ajadigital.core.assessment.domain.models.*

interface SubmissionStoreOutputPort {
    fun create(request: SubmissionStoreCreateDto): Map<String, Any?>
    fun submissions(request: SubmissionStoreSubmissionsDto): SubmissionPage
    fun pendingFeedbackCount(request: SubmissionStorePendingFeedbackCountDto): Int
    fun submission(request: SubmissionStoreSubmissionDto): Map<String, Any?>?
    fun update(request: SubmissionStoreUpdateDto): Map<String, Any?>?
    fun submit(request: SubmissionStoreSubmitDto): Map<String, Any?>?
    fun saveFeedback(request: SubmissionStoreSaveFeedbackDto): Map<String, Any?>?
    fun publishFeedback(request: SubmissionStorePublishFeedbackDto): Map<String, Any?>?
}
