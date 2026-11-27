package br.com.ajadigital.core.assessment.domain.models

class AttemptQuestionImage(fields: List<AttemptQuestionImageField>, val alt: String?, val src: String?) {
    val fields: List<AttemptQuestionImageField> = java.util.Collections.unmodifiableList(fields.toList())
}
