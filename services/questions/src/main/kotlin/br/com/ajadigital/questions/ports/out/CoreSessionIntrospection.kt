package br.com.ajadigital.questions.ports.out

interface CoreSessionIntrospection {
    fun requireActiveSession(cookie: String?)

    fun practiceBlocked(
        cookie: String?,
        questionId: String,
    ): Boolean
}
