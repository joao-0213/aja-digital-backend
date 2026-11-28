package br.com.ajadigital.questions.ports.out

import br.com.ajadigital.questions.domain.question.Question
import br.com.ajadigital.questions.domain.question.QuestionFilters
import br.com.ajadigital.questions.domain.question.QuestionPage

interface QuestionCatalog {
    fun findPage(
        filters: QuestionFilters,
        page: Int,
        pageSize: Int,
        sort: String,
    ): QuestionPage

    fun findAvailable(id: String): Question?

    fun findAvailableBatch(ids: List<String>): List<Question>
}
