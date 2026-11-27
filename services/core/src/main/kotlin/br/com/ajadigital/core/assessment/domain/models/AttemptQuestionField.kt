package br.com.ajadigital.core.assessment.domain.models

enum class AttemptQuestionField(val key: String) {
    ID("id"), YEAR("year"), IMAGE("image"), TOPIC("topic"), CORRECT("correct"), OPTIONS("options"),
    SUBJECT("subject"), STATEMENT("statement"), DIFFICULTY("difficulty"), EXPLANATION("explanation"), INSTITUTION("institution");

    companion object {
        fun fromKey(key: String): AttemptQuestionField? = entries.firstOrNull { it.key == key }
    }
}
