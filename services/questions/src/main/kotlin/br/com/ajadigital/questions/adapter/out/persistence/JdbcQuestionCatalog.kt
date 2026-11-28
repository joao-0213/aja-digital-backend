package br.com.ajadigital.questions.adapter.out.persistence

import br.com.ajadigital.questions.domain.question.Question
import br.com.ajadigital.questions.domain.question.QuestionFilters
import br.com.ajadigital.questions.domain.question.QuestionImage
import br.com.ajadigital.questions.domain.question.QuestionPage
import br.com.ajadigital.questions.ports.out.QuestionCatalog
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class JdbcQuestionCatalog(
    private val jdbc: JdbcTemplate,
) : QuestionCatalog {
    private val accentedCharacters = "áàâãäéèêëíìîïóòôõöúùûüç"
    private val baseCharacters = "aaaaaeeeeiiiiooooouuuuc"

    override fun findPage(
        filters: QuestionFilters,
        page: Int,
        pageSize: Int,
        sort: String,
    ): QuestionPage {
        val (where, parameters) = whereClause(filters)
        val total =
            jdbc.queryForObject(
                "select count(*) from questions.question_bank$where",
                Long::class.javaObjectType,
                *parameters.toTypedArray(),
            )
                ?: 0L
        val order =
            when (sort) {
                "-id" -> "id desc"
                "createdAt" -> "created_at asc, id asc"
                "-createdAt" -> "created_at desc, id asc"
                else -> "id asc"
            }
        val items =
            jdbc.query(
                "select * from questions.question_bank$where order by $order limit ? offset ?",
                { rs, _ -> rs.toQuestion() },
                *(parameters + listOf(pageSize, (page.toLong() - 1) * pageSize)).toTypedArray(),
            )
        return QuestionPage(items, page, pageSize, total)
    }

    override fun findAvailable(id: String): Question? = jdbc
        .query(
            "select * from questions.question_bank where id = ? and withdrawn_at is null",
            { rs, _ -> rs.toQuestion() },
            id,
        ).firstOrNull()

    override fun findAvailableBatch(ids: List<String>): List<Question> {
        val found =
            jdbc
                .query(
                    "select * from questions.question_bank where id in (${ids.joinToString(",") { "?" }}) and withdrawn_at is null",
                    { rs, _ -> rs.toQuestion() },
                    *ids.toTypedArray(),
                ).associateBy(Question::id)
        return ids.mapNotNull(found::get)
    }

    private fun whereClause(filters: QuestionFilters): Pair<String, List<Any>> {
        val clauses = mutableListOf("withdrawn_at is null")
        val parameters = mutableListOf<Any>()
        filters.q?.trim()?.takeIf(String::isNotEmpty)?.let { query ->
            val searchableColumns = listOf("subject", "topic", "institution", "statement", "array_to_string(options, ' ')")
            clauses +=
                searchableColumns.joinToString(" or ", "(", ")") { column ->
                    "position(${fold("?")} in ${fold(column)}) > 0"
                }
            repeat(searchableColumns.size) { parameters += query }
        }
        listOf("subject" to filters.subject, "topic" to filters.topic, "institution" to filters.institution).forEach { (column, value) ->
            value?.trim()?.takeIf(String::isNotEmpty)?.let {
                clauses += "position(lower(?) in lower($column)) > 0"
                parameters += it
            }
        }
        filters.difficulty?.let {
            clauses += "difficulty = ?"
            parameters += it
        }
        filters.year?.let {
            clauses += "year = ?"
            parameters += it
        }
        return " where ${clauses.joinToString(" and ")}" to parameters
    }

    private fun fold(expression: String) = "translate(lower($expression), '$accentedCharacters', '$baseCharacters')"

    private fun ResultSet.toQuestion(): Question {
        val imageId = getString("image_file_id")
        val image = imageId?.let { QuestionImage(it, getString("image_alt")) }
        val options = (getArray("options").array as Array<*>).map(Any?::toString)
        return Question(
            getString("id"),
            getInt("revision"),
            getTimestamp("created_at").toInstant(),
            getString("subject"),
            getString("topic"),
            getString("difficulty"),
            getString("institution"),
            getInt("year"),
            getString("statement"),
            options,
            getInt("correct_option"),
            getString("explanation"),
            image,
        )
    }
}
