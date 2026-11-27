package br.com.ajadigital.core

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.transaction.annotation.Transactional

/** Rules currently safe to run on the mixed legacy/target production tree. */
class CoreArchUnitTests {
    private val production = ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("br.com.ajadigital.core")

    @Test
    fun `production import contains required features and declarations`() {
        listOf("identity", "curriculum", "materials", "assessment", "announcements", "forum", "attendance", "dashboard", "outbox", "health", "shared")
            .forEach { feature ->
                assertTrue(production.any { it.packageName.startsWith("br.com.ajadigital.core.$feature") }, "Missing production feature: $feature")
            }
        assertTrue(production.any { it.simpleName == "CoreApplication" }, "CoreApplication was not imported")
    }

    @Test
    fun `legacy and target domain packages avoid framework and adapter dependencies`() {
        val selected = production.filter { it.packageName.contains(".domain.") }
        assertTrue(selected.isNotEmpty(), "No domain classes selected")
        domainIsolation().check(production)
    }

    @Test
    fun `inbound adapters do not depend on concrete use cases`() {
        val selected = production.filter { it.packageName.contains(".adapters.in.") }
        assertTrue(selected.isNotEmpty(), "No inbound adapters selected")
        inboundBoundary().check(production)
    }

    @Test
    fun `port contracts exist under legacy or target application packages`() {
        val ports = production.filter {
            it.isInterface && (it.packageName.contains(".domain.ports.") || it.packageName.contains(".application.ports."))
        }
        assertTrue(ports.isNotEmpty(), "No port contracts selected")
        val wrongNames = ports.filterNot { it.simpleName.endsWith("InputPort") || it.simpleName.endsWith("OutputPort") }
        assertTrue(wrongNames.isEmpty(), wrongNames.joinToString { it.name })
    }

    @Test
    fun `policy and dashboard compatibility selectors are non-vacuous`() {
        val policies = production.filter {
            val pkg = it.packageName
            pkg.endsWith(".domain.policies") || pkg.contains(".domain.policies.")
        }
        assertTrue(policies.isNotEmpty(), "No domain policies selected")
        val dashboards = production.filter {
            it.simpleName == "GetDashboardUseCase" &&
                (it.packageName.contains(".dashboard.domain.usecases") || it.packageName.contains(".dashboard.application.usecases"))
        }
        assertTrue(dashboards.isNotEmpty(), "No old/target dashboard use case selected")
    }

    @Test
    fun `all currently compatible bytecode guarantees pass`() {
        val violations = listOf(domainIsolation(), inboundBoundary(), noControllerConnectionFactoryDependency(), dashboardDoesNotCallConcreteUseCases())
            .flatMap { rule -> rule.evaluate(production).failureReport.details }
        assertFalse(violations.isNotEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `controllers stay outside persistence and transaction plumbing`() {
        val controllers = production.filter { it.simpleName.endsWith("Controller") }
        assertTrue(controllers.isNotEmpty(), "No controllers selected")
        noClasses().that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAnyPackage("javax.sql..", "java.sql..", "org.springframework.transaction..")
            .check(production)
        noControllerConnectionFactoryDependency().check(production)
        assertTrue(controllers.none { it.isAnnotatedWith(Transactional::class.java) }, "Controllers must not be transactional")
        val app = production.singleOrNull { it.simpleName == "CoreApplication" }
        assertTrue(app != null, "CoreApplication missing")
        assertTrue(app!!.methods.none { it.isAnnotatedWith(org.springframework.context.annotation.Bean::class.java) }, "CoreApplication must delegate bean wiring")
    }

    @Test
    fun `bytecode rules reject bad boundaries while accepting cohesive inbound dependencies`() {
        val fixtures = ClassFileImporter().importClasses(
            br.com.ajadigital.core.architecture.fixtures.adapters.`in`.controllers.InboundAdapterFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.domain.usecases.ConcreteFixtureUseCase::class.java,
            br.com.ajadigital.core.architecture.fixtures.adapters.out.OutboundAdapterFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.adapters.`in`.controllers.models.InboundModelFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.adapters.`in`.controllers.InboundFacadeFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.domain.models.ForbiddenDomainFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.domain.models.PureDomainFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.cycle.left.LeftFeatureFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.cycle.right.RightFeatureFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.shared.SharedFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.feature.FeatureFixture::class.java,
            br.com.ajadigital.core.architecture.fixtures.adapters.`in`.controllers.RabbitLeakingController::class.java,
            br.com.ajadigital.core.architecture.fixtures.adapters.`in`.controllers.SpringRabbitLeakingController::class.java,
            br.com.ajadigital.core.dashboard.domain.usecases.DashboardProjectionInputPortFixture::class.java,
            br.com.ajadigital.core.dashboard.domain.usecases.DashboardConcreteUseCase::class.java,
            br.com.ajadigital.core.dashboard.domain.usecases.LegacyDashboardGetDashboardUseCase::class.java,
            br.com.ajadigital.core.dashboard.domain.usecases.SafeDashboardGetDashboardUseCase::class.java,
            br.com.ajadigital.core.dashboard.application.usecases.TargetDashboardConcreteUseCase::class.java,
            br.com.ajadigital.core.dashboard.application.usecases.NewDashboardGetDashboardUseCase::class.java,
        )
        val concreteUseCaseFailure = inboundBoundary().evaluate(fixtures).failureReport.details
        assertTrue(concreteUseCaseFailure.any { it.contains("InboundAdapterFixture") && it.contains("ConcreteFixtureUseCase") }, concreteUseCaseFailure.toString())
        val directionFailure = forbiddenAdapterDirection().evaluate(fixtures).failureReport.details
        assertTrue(directionFailure.any { it.contains("InboundAdapterFixture") && it.contains("OutboundAdapterFixture") }, directionFailure.toString())
        assertTrue(directionFailure.none { it.contains("InboundFacadeFixture") || it.contains("InboundModelFixture") }, directionFailure.toString())
        val domainFailure = domainIsolation().evaluate(fixtures).failureReport.details
        assertTrue(domainFailure.any { it.contains("ForbiddenDomainFixture") && it.contains("java.sql.Connection") }, domainFailure.toString())
        assertTrue(domainFailure.none { it.contains("PureDomainFixture") }, domainFailure.toString())
        val cycleFailure = com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
            .matching("br.com.ajadigital.core.architecture.fixtures.cycle.(*)..").should().beFreeOfCycles()
            .evaluate(fixtures).failureReport.details
        assertTrue(cycleFailure.any { it.contains("LeftFeatureFixture") && it.contains("RightFeatureFixture") }, cycleFailure.toString())
        val sharedLeak = noClasses().that().resideInAPackage("..fixtures.shared..")
            .should().dependOnClassesThat().resideInAPackage("..fixtures.feature..")
            .evaluate(fixtures).failureReport.details
        assertTrue(sharedLeak.any { it.contains("SharedFixture") && it.contains("FeatureFixture") }, sharedLeak.toString())
        val connectionFactoryLeak = noControllerConnectionFactoryDependency().evaluate(fixtures).failureReport.details
        assertTrue(connectionFactoryLeak.any { it.contains("RabbitLeakingController") }, connectionFactoryLeak.toString())
        assertTrue(connectionFactoryLeak.any { it.contains("SpringRabbitLeakingController") }, connectionFactoryLeak.toString())
        assertTrue(connectionFactoryLeak.none { it.contains("InboundFacadeFixture") }, connectionFactoryLeak.toString())
        val dashboardRule = dashboardDoesNotCallConcreteUseCases()
        val dashboardFailure = dashboardRule.evaluate(fixtures).failureReport.details
        assertTrue(dashboardFailure.any { it.contains("LegacyDashboardGetDashboardUseCase") }, dashboardFailure.toString())
        assertTrue(dashboardFailure.any { it.contains("NewDashboardGetDashboardUseCase") }, dashboardFailure.toString())
        assertTrue(dashboardFailure.none { it.contains("SafeDashboardGetDashboardUseCase") }, dashboardFailure.toString())
    }

    @Test
    fun `curriculum ports have typed contracts and avoid Academic and selected Identity packages`() {
        val classes = production.toList()
        val ports = CoreContractTypeAnalyzer.selectRoots(classes).filter {
            it.packageName.startsWith("br.com.ajadigital.core.curriculum.") && it.simpleName.endsWith("Port")
        }
        assertTrue(ports.any { it.simpleName == "SubjectStoreOutputPort" })
        assertTrue(ports.any { it.simpleName == "ListSubjectsInputPort" })
        val findings = CoreContractTypeAnalyzer.analyze(classes, ports)
        assertTrue(findings.isEmpty(), findings.joinToString("\n"))
        noClasses().that().resideInAPackage("..curriculum..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..academic..", "..identity.application.models..", "..identity.application.usecases..",
                "..identity.application.ports.out..", "..identity.adapters..", "..identity.configuration..",
            )
            .check(production)
    }

    @Test
    fun `materials ports use typed contracts and feature dependencies stay within the inventory`() {
        val classes = production.toList()
        val ports = CoreContractTypeAnalyzer.selectRoots(classes).filter {
            it.packageName.startsWith("br.com.ajadigital.core.materials.") && it.simpleName.endsWith("Port")
        }
        val portNames = ports.map { it.simpleName }.toSet()
        assertTrue(portNames.containsAll(setOf("FileStoreOutputPort", "MaterialWriteOutputPort", "PublishMaterialsInputPort", "GetDashboardContentInputPort")))
        val findings = CoreContractTypeAnalyzer.analyze(classes, ports)
        assertTrue(findings.isEmpty(), findings.joinToString("\n"))
        noClasses().that().resideInAPackage("..materials..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..academic..", "..curriculum..", "..assessment..", "..forum..", "..dashboard..",
                "..identity.application.models..", "..identity.application.usecases..",
                "..identity.application.ports.out..", "..identity.adapters..", "..identity.configuration..",
            )
            .check(production)
    }

    @Test
    fun `assessment authoring ports have typed contracts`() {
        val classes = production.toList()
        val names = setOf(
            "CreateAssessmentInputPort", "UpdateAssessmentInputPort", "PublishAssessmentInputPort",
            "GetAssessmentInputPort", "ListAssessmentsInputPort", "ListDashboardAssessmentsInputPort",
            "CountPendingStudentAssessmentsInputPort", "AssessmentReadOutputPort", "AssessmentWriteOutputPort",
            "QuestionSnapshotProviderOutputPort",
        )
        val ports = CoreContractTypeAnalyzer.selectRoots(classes).filter {
            it.packageName.startsWith("br.com.ajadigital.core.assessment.") && it.simpleName in names
        }
        assertEquals(names, ports.map { it.simpleName }.toSet())
        val findings = CoreContractTypeAnalyzer.analyze(classes, ports)
        assertTrue(findings.isEmpty(), findings.joinToString("\n"))
    }

    @Test
    fun `attempt ports use typed application contracts`() {
        val names = setOf(
            "AttemptStoreOutputPort", "CheckPracticeBlockedInputPort", "GetAttemptInputPort", "GetAttemptResultInputPort",
            "ListAttemptsInputPort", "SaveAttemptAnswersInputPort", "StartAttemptInputPort", "SubmitAttemptInputPort",
        )
        val ports = CoreContractTypeAnalyzer.selectRoots(production.toList()).filter {
            it.packageName.startsWith("br.com.ajadigital.core.assessment.") && it.simpleName in names
        }
        assertEquals(names, ports.map { it.simpleName }.toSet())
        assertTrue(ports.all { it.packageName.contains(".application.ports.") })
        val findings = CoreContractTypeAnalyzer.analyze(production.toList(), ports)
        assertTrue(findings.isEmpty(), findings.joinToString("\n"))
    }

    @Test
    fun `submission ports use typed application contracts`() {
        val names = setOf(
            "SubmissionStoreOutputPort", "CountPendingFeedbackInputPort", "CreateSubmissionInputPort",
            "GetSubmissionInputPort", "ListSubmissionsInputPort", "PublishSubmissionFeedbackInputPort",
            "SaveSubmissionFeedbackInputPort", "SubmitSubmissionInputPort", "UpdateSubmissionInputPort",
        )
        val ports = CoreContractTypeAnalyzer.selectRoots(production.toList()).filter {
            it.packageName.startsWith("br.com.ajadigital.core.assessment.") && it.simpleName in names
        }
        assertEquals(names, ports.map { it.simpleName }.toSet())
        assertTrue(ports.all { it.packageName.contains(".application.ports.") || it.simpleName == "SubmissionStoreOutputPort" })
        val findings = CoreContractTypeAnalyzer.analyze(production.toList(), ports)
        assertTrue(findings.isEmpty(), findings.joinToString("\n"))
    }

    @Test
    fun `announcement ports use typed application contracts`() {
        val names = setOf(
            "AnnouncementStoreOutputPort", "CreateAnnouncementInputPort", "GetAnnouncementInputPort",
            "ListAnnouncementsInputPort", "ListCalendarEventsInputPort", "ListDashboardAnnouncementsInputPort",
            "ListDashboardUpcomingEventsInputPort", "UpdateAnnouncementInputPort", "WithdrawAnnouncementInputPort",
            "AnnouncementReceiptCodecOutputPort", "OutboxEventProducerInputPort", "InsertOutboxEventOutputPort",
        )
        val ports = CoreContractTypeAnalyzer.selectRoots(production.toList()).filter {
            (it.packageName.startsWith("br.com.ajadigital.core.announcements.") || it.packageName.startsWith("br.com.ajadigital.core.outbox.")) && it.simpleName in names
        }
        assertEquals(names, ports.map { it.simpleName }.toSet())
        assertTrue(ports.all { it.packageName.contains(".application.ports.") })
        val findings = CoreContractTypeAnalyzer.analyze(production.toList(), ports)
        assertTrue(findings.isEmpty(), findings.joinToString("\n"))
    }

    @Test
    fun `contract walker preserves project generic bindings and only allows exact transaction callback`() {
        val fixtures = ClassFileImporter().importClasses(
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.Page::class.java,
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.Envelope::class.java,
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.Item::class.java,
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.ValidContractData::class.java,
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.ForwardingBox::class.java,
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.ValidForwardedContractData::class.java,
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.ValidPageOutputPort::class.java,
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.InvalidNestedOutputPort::class.java,
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.InvalidUnboundOutputPort::class.java,
            br.com.ajadigital.core.architecture.contractfixtures.application.contracts.TransactionRunnerOutputPort::class.java,
            br.com.ajadigital.core.architecture.transactioninvalid.application.contracts.TransactionWrapper::class.java,
            br.com.ajadigital.core.architecture.transactioninvalid.application.contracts.TransactionRunnerOutputPort::class.java,
        )
        val roots = CoreContractTypeAnalyzer.selectRoots(fixtures.toList())
        assertTrue(roots.isNotEmpty(), "No contract fixtures selected")
        val findings = CoreContractTypeAnalyzer.analyze(fixtures.toList(), roots)
        assertTrue(findings.none {
            it.startsWith("br.com.ajadigital.core.architecture.contractfixtures.application.contracts.ValidContractData") ||
                it.startsWith("br.com.ajadigital.core.architecture.contractfixtures.application.contracts.ValidForwardedContractData") ||
                it.startsWith("br.com.ajadigital.core.architecture.contractfixtures.application.contracts.ValidPageOutputPort") ||
                it.startsWith("br.com.ajadigital.core.architecture.contractfixtures.application.contracts.TransactionRunnerOutputPort")
        }, findings.toString())
        assertTrue(findings.any { it.contains("InvalidNestedOutputPort") && it.contains("java.lang.Object") }, findings.toString())
        assertTrue(findings.any { it.contains("InvalidUnboundOutputPort") && it.contains("generic type variable T") }, findings.toString())
        assertTrue(findings.any { it.contains("transactioninvalid") && it.contains("only <T> run(block: () -> T): T is permitted") }, findings.toString())
    }

    private fun domainIsolation(): ArchRule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "tools.jackson..", "com.fasterxml.jackson..", "java.sql..", "javax.sql..",
            "com.rabbitmq..", "jakarta..", "java.net.http..", "..adapters..", "..application..", "..configuration..",
        )

    private fun inboundBoundary(): ArchRule = noClasses()
        .that().resideInAPackage("..adapters.in..")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("UseCase")

    private fun forbiddenAdapterDirection(): ArchRule = noClasses()
        .that().resideInAPackage("..adapters.in..")
        .should().dependOnClassesThat().resideInAPackage("..adapters.out..")

    private fun noControllerConnectionFactoryDependency(): ArchRule = noClasses().that().haveSimpleNameEndingWith("Controller")
        .should().dependOnClassesThat().haveSimpleName("ConnectionFactory")

    private fun dashboardDoesNotCallConcreteUseCases(): ArchRule = noClasses()
        .that().haveSimpleNameEndingWith("GetDashboardUseCase")
        .and().resideInAnyPackage("..dashboard.domain.usecases..", "..dashboard.application.usecases..")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("UseCase")
}
