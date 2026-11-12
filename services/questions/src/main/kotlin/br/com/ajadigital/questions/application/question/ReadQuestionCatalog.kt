package br.com.ajadigital.questions.application.question

import br.com.ajadigital.questions.domain.question.Question
import br.com.ajadigital.questions.domain.question.QuestionFilters
import br.com.ajadigital.questions.domain.question.QuestionPage
import br.com.ajadigital.questions.domain.question.QuestionSnapshot
import br.com.ajadigital.questions.domain.question.toSnapshot
import br.com.ajadigital.questions.ports.out.QuestionCatalog

class QuestionNotFound : RuntimeException()
class InvalidQuestionRequest : RuntimeException()
class QuestionCatalogChanged : RuntimeException()

class ReadQuestionCatalog(private val catalog: QuestionCatalog) {
    private val validId = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

    fun list(filters: QuestionFilters, page: Int, pageSize: Int, sort: String): QuestionPage {
        if (page < 1 || pageSize !in 1..100 || sort !in setOf("id", "-id", "createdAt", "-createdAt")) {
            throw InvalidQuestionRequest()
        }
        return catalog.findPage(filters, page, pageSize, sort)
    }

    fun get(id: String): Question {
        if (!validId.matches(id)) throw InvalidQuestionRequest()
        return catalog.findAvailable(id) ?: throw QuestionNotFound()
    }

    fun snapshots(ids: List<String>): List<QuestionSnapshot> {
        if (ids.isEmpty() || ids.size > 100 || ids.distinct().size != ids.size || ids.any { !validId.matches(it) }) {
            throw InvalidQuestionRequest()
        }
        val questions = catalog.findAvailableBatch(ids).associateBy(Question::id)
        if (questions.size != ids.size) throw QuestionNotFound()
        val ordered = ids.map { questions.getValue(it) }
        return ordered.map(Question::toSnapshot)
    }
}
