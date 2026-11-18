package br.com.ajadigital.core

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/** Bounded PSI rules shared by production diagnostics and test resources. */
internal object CoreArchitectureSourceAnalyzer {
    fun analyze(path: String, file: KtFile): List<String> = buildList {
        val pkg = file.packageFqName.asString()
        val domain = pkg.isPackageOrDescendant("domain")
        val application = pkg.isPackageOrDescendant("application")
        val policy = pkg.isPackageOrDescendant("domain.policies")
        val normalized = path.replace('\\', '/')

        if (pkg.startsWith("br.com.ajadigital.core.") && pkg.isPackageOrDescendant("classroom")) add("obsolete classroom feature name; target is academic")
        if (pkg.isPackageOrDescendant("domain.ports") || pkg.isPackageOrDescendant("domain.usecases") ||
            normalized.contains("/domain/ports/") || normalized.contains("/domain/usecases/")) add("legacy port/use case placement; target is application")
        if (domain) file.importDirectives.forEach { directive ->
            val imported = directive.importPath?.pathStr.orEmpty()
            if (listOf("org.springframework", "jakarta", "java.sql", "javax.sql", "com.rabbitmq").any { imported == it || imported.startsWith("$it.") } || imported.contains(".adapters.")) {
                add("domain imports framework or adapter: $imported")
            }
        }
        if (application) file.importDirectives.forEach { directive ->
            val imported = directive.importPath?.pathStr.orEmpty()
            if (imported.contains(".adapters.") || imported.contains(".configuration.")) add("application imports adapter: $imported")
        }
        if (policy) file.importDirectives.forEach { directive ->
            val imported = directive.importPath?.pathStr.orEmpty()
            if (imported.contains(".ports.") || imported.contains(".adapters.") || imported.startsWith("java.io.") ||
                imported.startsWith("java.sql.") || imported.startsWith("java.net.")) add("policy imports I/O or a port: $imported")
        }
        if (application && (pkg.isPackageOrDescendant("application.contracts") || pkg.contains(".application.ports.")) &&
            file.collectDescendantsOfType<KtTypeReference>().any { type ->
                val text = type.text
                text == "Any" || text == "Any?" || text == "JsonNode" || text.contains("<*>") || text.contains(", Any")
            }) add("contract uses an unbounded or generic escape type")
        if ((domain || application) && file.importDirectives.any {
                it.importPath?.pathStr?.contains("HttpStatus") == true || it.importPath?.pathStr?.contains("ResponseStatus") == true
            }) add("domain/application imports HTTP status")
        if (domain || application) {
            if (file.collectDescendantsOfType<KtPostfixExpression>().any { it.operationReference.text == "!!" }) add("domain/application uses unsafe non-null assertion")
            if (file.collectDescendantsOfType<KtAnnotationEntry>().any { it.shortName?.asString() == "Suppress" }) add("domain/application suppression requires justification")
            primaryDeclarationFinding(normalized.substringAfterLast('/'), file)?.let(::add)
        }
    }

    private fun primaryDeclarationFinding(filename: String, file: KtFile): String? {
        val declarations = file.declarations.filterIsInstance<KtClassOrObject>()
        if (declarations.isEmpty()) return null
        val name = filename.removeSuffix(".kt")
        val primary = declarations.singleOrNull { it.name == name } ?: return "primary declaration must match filename"
        val extras = declarations.filterNot { it === primary }
        return if (extras.any { extra ->
                val privateHelper = extra.hasModifier(KtTokens.PRIVATE_KEYWORD)
                val smallResult = listOf("Result", "Outcome", "Decision").any { extra.name.orEmpty().endsWith(it) }
                val sealedSubtype = primary.hasModifier(KtTokens.SEALED_KEYWORD) && extra.superTypeListEntries.any { it.text.startsWith(name) }
                !(privateHelper || smallResult || sealedSubtype)
            }) "multiple primary declarations" else null
    }

    private fun String.isPackageOrDescendant(suffix: String): Boolean =
        endsWith(".$suffix") || contains(".$suffix.")
}
