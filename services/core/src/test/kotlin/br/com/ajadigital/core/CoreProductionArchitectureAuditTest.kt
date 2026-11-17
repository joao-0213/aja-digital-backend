package br.com.ajadigital.core

import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.K1Deprecation
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Intentionally diagnostic during migration: report every finding and fail while any remain. */
@OptIn(K1Deprecation::class)
class CoreProductionArchitectureAuditTest {
    @Test
    fun `production architecture audit writes a report and fails on violations`() {
        val findings = mutableListOf<String>()
        val imported = ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.ajadigital.core")
        if (imported.none { it.packageName.startsWith("br.com.ajadigital.core.academic") }) {
            findings += "Core target package academic is missing (D-02 classroom rename incomplete)"
        }
        check(imported.any { it.simpleName.endsWith("Controller") }) { "No controller classes selected" }
        check(imported.any {
            it.simpleName == "GetDashboardUseCase" && (it.packageName.contains(".dashboard.domain.usecases") || it.packageName.contains(".dashboard.application.usecases"))
        }) { "No legacy/target GetDashboardUseCase selected" }
        listOf(
            noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..", "javax.sql..", "..adapters..", "..application..", "..configuration.."),
            noClasses().that().resideInAPackage("..adapters.in..").should().dependOnClassesThat().haveSimpleNameEndingWith("UseCase"),
            noClasses().that().resideInAPackage("..adapters.in..").should().dependOnClassesThat().resideInAPackage("..adapters.out.."),
            noClasses().that().resideInAPackage("..adapters.out..").should().dependOnClassesThat().resideInAPackage("..adapters.in.."),
            noClasses().that().haveSimpleNameEndingWith("Controller").should().dependOnClassesThat()
                .haveSimpleName("ConnectionFactory"),
            noClasses().that().haveSimpleNameEndingWith("GetDashboardUseCase")
                .and().resideInAnyPackage("..dashboard.domain.usecases..", "..dashboard.application.usecases..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("UseCase"),
            noClasses().that().resideInAPackage("..shared..").should().dependOnClassesThat().resideInAnyPackage(
                "..identity..", "..classroom..", "..academic..", "..curriculum..", "..materials..", "..assessment..", "..announcements..", "..forum..", "..attendance..", "..dashboard..", "..outbox..",
            ),
            noClasses().that().resideInAPackage("..domain.policies..").should().dependOnClassesThat()
                .resideInAnyPackage("..application..", "..adapters..", "..configuration..", "org.springframework..", "java.sql..", "javax.sql.."),
        ).forEach { rule -> findings += rule.evaluate(imported).failureReport.details }
        val cycles = com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
            .matching("br.com.ajadigital.core.(*)..").should().beFreeOfCycles()
        findings += cycles.evaluate(imported).failureReport.details
        findings += CoreContractTypeAnalyzer.analyze(imported)

        val disposable = Disposer.newDisposable()
        try {
            val env = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
            val factory = KtPsiFactory(env.project, false)
            val root = Path.of("src/main/kotlin/br/com/ajadigital/core")
            val sourceFiles = Files.walk(root).use { stream -> stream.filter { it.toString().endsWith(".kt") }.toList() }
            assertTrue(sourceFiles.isNotEmpty(), "No production Kotlin sources selected")
            sourceFiles.forEach { path ->
                val file = factory.createFile(path.fileName.toString(), Files.readString(path))
                findings += CoreArchitectureSourceAnalyzer.analyze(path.toString(), file).map { "$path: $it" }
            }
        } finally {
            Disposer.dispose(disposable)
        }

        val report = Path.of("build/reports/architectureAudit/core-violations.txt")
        Files.createDirectories(report.parent)
        Files.writeString(report, findings.distinct().sorted().joinToString("\n"))
        assertTrue(findings.isEmpty(), "${findings.size} architecture findings; full report: $report\n${findings.take(30).joinToString("\n")}")
    }

}
