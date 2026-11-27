package br.com.ajadigital.core.assessment.domain.models

enum class AttemptQuestionImageField(val key: String) {
    ALT("alt"), SRC("src");

    companion object {
        fun fromKey(key: String): AttemptQuestionImageField? = entries.firstOrNull { it.key == key }
    }
}
