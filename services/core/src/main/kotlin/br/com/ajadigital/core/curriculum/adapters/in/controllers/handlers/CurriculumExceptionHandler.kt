package br.com.ajadigital.core.curriculum.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.curriculum.domain.exceptions.InvalidAcademicReference
import br.com.ajadigital.core.curriculum.domain.exceptions.UnknownPublication
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice


@RestControllerAdvice
class CurriculumExceptionHandler {
    @ExceptionHandler(UnknownPublication::class)
    fun invalidPublication() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "Publication is not visible in this subject"))
    @ExceptionHandler(InvalidAcademicReference::class)
    fun invalidReference() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "A referenced user is not eligible"))
}
