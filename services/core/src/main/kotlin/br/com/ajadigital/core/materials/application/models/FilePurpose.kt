package br.com.ajadigital.core.materials.application.models

enum class FilePurpose(
    val value: String,
) {
    MATERIAL("material"),
    WORK("work"),
    FORUM("forum"),
    QUESTION("question"),
    ASSESSMENT("assessment"),
    ;

    val publicValue: String get() = if (this == ASSESSMENT) QUESTION.value else value
}
