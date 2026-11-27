package br.com.ajadigital.core.assessment.domain.models

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Collections

sealed interface SubmissionView {
    val id: String
    val revision: Int
    val createdAt: String
    val updatedAt: String
    val updatedBy: String
    val assessmentId: String
    val userId: String
    val attachments: List<Attachment>
    val submittedAt: String?
    val receipt: Receipt?
    val feedbackPublished: Feedback?
    val feedbackUpdated: Boolean

    data class Attachment(val kind: String, val name: String, val fileId: String? = null, val url: String? = null)
    data class Author(val id: String, val name: String)
    sealed interface Grade {
        data class Integer(val value: Int) : Grade
        data class LongInteger(val value: Long) : Grade
        data class Floating(val value: Double) : Grade
        data class Decimal(val value: BigDecimal) : Grade
        data class BigIntegerValue(val value: BigInteger) : Grade

        fun toNumber(): Number = when (this) {
            is Integer -> value
            is LongInteger -> value
            is Floating -> value
            is Decimal -> value
            is BigIntegerValue -> value
        }
    }
    data class Feedback(val comment: String, val grade: Grade?, val author: Author, val createdAt: String)
    data class Receipt(val id: String, val resourceId: String, val submittedAt: String)

    class StudentPending(
        override val id: String, override val revision: Int, override val createdAt: String, override val updatedAt: String,
        override val updatedBy: String, override val assessmentId: String, override val userId: String,
        attachments: List<Attachment>,
    ) : SubmissionView {
        override val attachments = Collections.unmodifiableList(attachments.toList())
        override val submittedAt: String? = null
        override val receipt: Receipt? = null
        override val feedbackPublished: Feedback? = null
        override val feedbackUpdated: Boolean = false
    }

    class StudentSubmitted(
        override val id: String, override val revision: Int, override val createdAt: String, override val updatedAt: String,
        override val updatedBy: String, override val assessmentId: String, override val userId: String,
        attachments: List<Attachment>, override val submittedAt: String, override val receipt: Receipt,
        override val feedbackPublished: Feedback?, override val feedbackUpdated: Boolean,
    ) : SubmissionView {
        override val attachments = Collections.unmodifiableList(attachments.toList())
    }

    class StaffSubmitted(
        override val id: String, override val revision: Int, override val createdAt: String, override val updatedAt: String,
        override val updatedBy: String, override val assessmentId: String, override val userId: String,
        attachments: List<Attachment>, override val submittedAt: String, override val receipt: Receipt,
        override val feedbackPublished: Feedback?, override val feedbackUpdated: Boolean,
        val studentName: String, val feedbackDraft: Feedback?, feedbackHistory: List<Feedback>,
    ) : SubmissionView {
        override val attachments = Collections.unmodifiableList(attachments.toList())
        val feedbackHistory = Collections.unmodifiableList(feedbackHistory.toList())
    }
}
