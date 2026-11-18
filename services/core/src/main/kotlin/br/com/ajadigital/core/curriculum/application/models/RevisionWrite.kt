package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumConflict
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumNotFound


sealed interface RevisionWrite<out T> {
    data class Applied<T>(val value: T) : RevisionWrite<T>
    data class Conflict(val currentRevision: Int) : RevisionWrite<Nothing>
    data object Missing : RevisionWrite<Nothing>
}

internal fun <T> RevisionWrite<T>.valueOrThrow(): T = when (this) {
    is RevisionWrite.Applied -> value
    is RevisionWrite.Conflict -> throw CurriculumConflict(currentRevision)
    RevisionWrite.Missing -> throw CurriculumNotFound()
}
