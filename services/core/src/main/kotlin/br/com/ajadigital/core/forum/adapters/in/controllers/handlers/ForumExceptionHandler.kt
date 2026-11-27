package br.com.ajadigital.core.forum.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.forum.domain.exceptions.InvalidTopicReference
import br.com.ajadigital.core.forum.domain.exceptions.ForumQuestionSnapshotNotFound
import br.com.ajadigital.core.forum.domain.exceptions.ForumQuestionsUnavailable
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice


@RestControllerAdvice
class ForumExceptionHandler {
    @ExceptionHandler(InvalidTopicReference::class)
    fun invalidTopicReference() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "Referenced content is not visible in this subject"))
    @ExceptionHandler(ForumQuestionSnapshotNotFound::class)
    fun missingQuestion() = ResponseEntity.status(404).body(errorBody("NOT_FOUND", "Resource was not found"))
    @ExceptionHandler(ForumQuestionsUnavailable::class)
    fun unavailableQuestions() = ResponseEntity.status(503).body(errorBody("SERVICE_UNAVAILABLE", "Question snapshots are unavailable"))
}
