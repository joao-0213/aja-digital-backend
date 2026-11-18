package br.com.ajadigital.core

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@OptIn(K1Deprecation::class)
class CoreArchitectureFixtureTests {
    @Test
    fun `production PSI analyzer accepts valid source and reports each configured finding`() {
        val disposable = Disposer.newDisposable()
        try {
            val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
            val factory = KtPsiFactory(environment.project, false)
            assertTrue(analyze(factory, "architecture/valid/FeatureFile.kt").isEmpty())
            assertTrue(analyze(factory, "architecture/valid/FeatureFile.kt", "architecture\\valid\\FeatureFile.kt").isEmpty())
            assertTrue(analyze(factory, "architecture/valid/BaseEvent.kt").isEmpty())
            assertTrue(analyze(factory, "architecture/valid/AcademicFeature.kt").isEmpty())

            assertEquals(listOf("domain imports framework or adapter: org.springframework.stereotype.Service"), analyze(factory, "architecture/invalid/DomainFrameworkImport.kt"))
            assertEquals(listOf("contract uses an unbounded or generic escape type"), analyze(factory, "architecture/invalid/GenericContractEscape.kt"))
            assertEquals(listOf("policy imports I/O or a port: fixture.feature.domain.ports.out.Store"), analyze(factory, "architecture/invalid/PolicyPortImport.kt"))
            assertEquals(listOf("policy imports I/O or a port: fixture.feature.domain.ports.out.Store"), analyze(factory, "architecture/invalid/NestedPolicyPortImport.kt"))
            assertEquals(listOf("legacy port/use case placement; target is application"), analyze(factory, "architecture/invalid/LegacyUseCasePlacement.kt"))
            assertEquals(listOf("application imports adapter: fixture.feature.adapters.out.Repository"), analyze(factory, "architecture/invalid/ApplicationAdapterImport.kt"))
            assertEquals(listOf("obsolete classroom feature name; target is academic"), analyze(factory, "architecture/invalid/ClassroomFeature.kt"))
            assertEquals(listOf("primary declaration must match filename"), analyze(factory, "architecture/invalid/WrongName.kt"))
            assertEquals(listOf("multiple primary declarations"), analyze(factory, "architecture/invalid/MultipleDeclarations.kt"))
            assertEquals(
                listOf(
                    "domain imports framework or adapter: org.springframework.http.HttpStatus",
                    "domain/application imports HTTP status",
                ),
                analyze(factory, "architecture/invalid/HttpStatusInDomain.kt"),
            )
            assertEquals(listOf("domain/application uses unsafe non-null assertion"), analyze(factory, "architecture/invalid/UnsafeAssertion.kt"))
            assertEquals(listOf("domain/application suppression requires justification"), analyze(factory, "architecture/invalid/Suppression.kt"))
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun analyze(factory: KtPsiFactory, resource: String, sourcePath: String = resource): List<String> {
        val url = requireNotNull(javaClass.classLoader.getResource(resource)) { "Missing fixture: $resource" }
        val file = factory.createFile(resource, Files.readString(Path.of(url.toURI())))
        return CoreArchitectureSourceAnalyzer.analyze(sourcePath, file)
    }
}
