package br.com.ajadigital.core

import com.tngtech.archunit.core.domain.JavaClass

/** Exact provider exports used by each feature, reconciled with D-02's capability matrix. */
internal object CoreFeatureDependencyRules {
    private const val CORE = "br.com.ajadigital.core"
    private fun identity(type: String) = "$CORE.identity.$type"
    private fun outbox(type: String) = "$CORE.outbox.$type"
    private fun shared(type: String) = "$CORE.shared.$type"

    private val exports = mapOf(
        "identity" to mapOf("outbox" to setOf(
            outbox("application.contracts.PrepareIdentityNotificationCommand"),
            outbox("application.ports.in.OutboxEventProducerInputPort"),
            outbox("application.contracts.EncryptedOutboxPayload"),
        )),
        "academic" to mapOf(
            "identity" to setOf(identity("application.contracts.AuthenticatedActor"), identity("application.contracts.EmailAddress"), identity("application.ports.in.HashPasswordInputPort"), identity("application.ports.in.ResolveAuthenticatedActorInputPort")),
            "outbox" to setOf(outbox("application.contracts.PrepareIdentityNotificationCommand"), outbox("application.contracts.EncryptedOutboxPayload"), outbox("application.ports.in.OutboxEventProducerInputPort")),
        ),
        "announcements" to mapOf(
            "identity" to setOf(identity("application.contracts.AuthenticatedActor"), identity("application.ports.in.ResolveAuthenticatedActorInputPort")),
            "outbox" to setOf(outbox("application.contracts.AppendPreparedOutboxEventCommand"), outbox("application.contracts.PrepareOutboxEventCommand"), outbox("application.contracts.PreparedOutboxEvent"), outbox("application.ports.in.OutboxEventProducerInputPort")),
        ),
        "assessment" to mapOf(
            "identity" to setOf(identity("application.contracts.AuthenticatedActor"), identity("application.contracts.SessionRequired"), identity("application.ports.in.ResolveAuthenticatedActorInputPort")),
        ),
        "attendance" to mapOf(
            "identity" to setOf(identity("application.contracts.AuthenticatedActor"), identity("application.ports.in.ResolveAuthenticatedActorInputPort")),
        ),
        "forum" to mapOf(
            "identity" to setOf(identity("application.contracts.AuthenticatedActor"), identity("application.ports.in.ResolveAuthenticatedActorInputPort")),
        ),
        "materials" to mapOf(
            "identity" to setOf(identity("application.contracts.AuthenticatedActor"), identity("application.contracts.SessionRequired"), identity("application.ports.in.ResolveAuthenticatedActorInputPort")),
        ),
        "curriculum" to mapOf(
            "identity" to setOf(identity("application.contracts.AuthenticatedActor"), identity("application.ports.in.ResolveAuthenticatedActorInputPort")),
        ),
        "dashboard" to mapOf(
            "academic" to setOf("$CORE.academic.application.contracts.DashboardAcademicQuery", "$CORE.academic.application.contracts.DashboardAcademicProjection", "$CORE.academic.application.contracts.DashboardClassProjection", "$CORE.academic.application.ports.in.GetDashboardAcademicInputPort"),
            "announcements" to setOf("$CORE.announcements.application.contracts.ListDashboardAnnouncementsQuery", "$CORE.announcements.application.contracts.ListDashboardUpcomingEventsQuery", "$CORE.announcements.application.contracts.Announcement", "$CORE.announcements.application.contracts.AnnouncementCalendarEvent", "$CORE.announcements.application.ports.in.ListDashboardAnnouncementsInputPort", "$CORE.announcements.application.ports.in.ListDashboardUpcomingEventsInputPort"),
            "assessment" to setOf("$CORE.assessment.application.contracts.CountPendingFeedbackQuery", "$CORE.assessment.application.contracts.ListDashboardAssessmentsQuery", "$CORE.assessment.application.contracts.DashboardAssessment", "$CORE.assessment.application.ports.in.CountPendingFeedbackInputPort", "$CORE.assessment.application.ports.in.CountPendingStudentAssessmentsInputPort", "$CORE.assessment.application.ports.in.ListDashboardAssessmentsInputPort"),
            "curriculum" to setOf("$CORE.curriculum.application.contracts.ListSubjectsQuery", "$CORE.curriculum.application.contracts.SubjectTeacher", "$CORE.curriculum.application.contracts.Subject", "$CORE.curriculum.application.contracts.SubjectSummary", "$CORE.curriculum.application.ports.in.ListSubjectsInputPort"),
            "forum" to setOf("$CORE.forum.application.contracts.ListDashboardTopicsQuery", "$CORE.forum.application.contracts.ForumDashboardTopic", "$CORE.forum.application.ports.in.ListDashboardTopicsInputPort"),
            "identity" to setOf(identity("application.contracts.AuthenticatedActor"), identity("application.ports.in.ResolveAuthenticatedActorInputPort")),
            "materials" to setOf("$CORE.materials.application.contracts.LearningProgress", "$CORE.materials.application.contracts.ResumeMaterial", "$CORE.materials.application.contracts.DashboardContent", "$CORE.materials.application.contracts.MaterialKind", "$CORE.materials.application.ports.in.GetDashboardContentInputPort"),
        ),
    )

    private val sharedExports = setOf(
        shared("adapters.in.web.errors.ErrorBodyKt"), shared("adapters.in.web.errors.FeatureHttpErrors"),
        shared("adapters.in.web.requests.OrderCommandRequest"), shared("adapters.in.web.requests.OrderItemRequest"),
        shared("adapters.in.web.requests.RequestIdKt"), shared("adapters.in.web.requests.RequestIdempotencyKeyKt"),
        shared("adapters.in.web.requests.RequestRevisionKt"), shared("adapters.in.web.requests.RequestTextKt"),
        shared("adapters.in.web.requests.ToRequestValueKt"), shared("adapters.out.questions.QuestionsSnapshotHttpClient"),
        shared("adapters.out.questions.QuestionSnapshotWire"), shared("adapters.out.questions.QuestionSnapshotImageWire"),
        shared("application.contracts.FieldChange"), shared("application.contracts.FieldChangeKt"), shared("application.contracts.FieldChange\$Set"), shared("application.contracts.FieldChange\$Unchanged"),
        shared("application.contracts.Page"), shared("application.ports.out.EncodedIdempotencyStoreOutputPort"),
        shared("application.ports.out.TransactionRunnerOutputPort"), shared("domain.exceptions.IdempotencyKeyReuse"),
        shared("domain.models.FindIdempotencyDto"),
    )

    fun analyze(classes: List<JavaClass>, enforceInventory: Boolean = true): List<String> {
        val findings = mutableListOf<String>()
        val used = mutableSetOf<Pair<String, String>>()
        val modules = setOf("identity", "academic", "announcements", "assessment", "attendance", "forum", "materials", "curriculum", "dashboard", "outbox", "shared", "health")
        classes.mapNotNull { feature(it.packageName) }.distinct().filterNot(modules::contains)
            .forEach { findings += "Unknown Core module selector: $it" }
        if (enforceInventory) {
            modules.forEach { module ->
                if (classes.none { feature(it.packageName) == module }) findings += "No production classes selected for Core module $module"
            }
        }
        for (source in classes) {
            val consumer = feature(source.packageName) ?: continue
            for (dependency in source.directDependenciesFromSelf) {
                val target = dependency.targetClass
                val provider = feature(target.packageName) ?: continue
                if (consumer == provider) continue
                used += consumer to provider
                val allowed = when (provider) {
                    "shared" -> sharedExports
                    else -> exports[consumer]?.get(provider).orEmpty()
                }
                if (target.name !in allowed) findings += "${source.name} depends on non-exported $provider type ${target.name}"
                if (provider != "shared" && !(target.packageName.contains(".application.contracts") || target.packageName.contains(".application.ports.in"))) {
                    findings += "Exported $provider type ${target.name} must be an application contract or incoming port"
                }
            }
        }
        if (enforceInventory) {
            val expectedEdges = exports.flatMap { (consumer, providers) -> providers.keys.map { consumer to it } }
            expectedEdges.filterNot { it in used }.forEach { findings += "Declared feature edge ${it.first} -> ${it.second} has no production use" }
        }
        return findings.distinct().sorted()
    }

    private fun feature(packageName: String): String? {
        val prefix = "$CORE."
        if (!packageName.startsWith(prefix)) return null
        return packageName.removePrefix(prefix).substringBefore('.').takeIf(String::isNotEmpty)
    }
}
