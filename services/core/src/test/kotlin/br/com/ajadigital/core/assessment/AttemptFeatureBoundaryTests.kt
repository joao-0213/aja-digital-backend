package br.com.ajadigital.core.assessment

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class AttemptFeatureBoundaryTests {
    @Test
    fun `identity controller does not depend on assessment`() {
        val imported = ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.ajadigital.core.identity")

        val rule = noClasses().that().resideInAPackage("br.com.ajadigital.core.identity..")
            .should().dependOnClassesThat().resideInAPackage("..assessment..")

        rule.check(imported)
    }
}
