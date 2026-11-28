package br.com.ajadigital.core

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoAnnotationDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.RequestMapping
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.kotlinFunction
import kotlin.test.assertNotNull
import kotlin.test.assertTrue as assertCondition

class CoreKonsistTests {
    private val production = Konsist.scopeFromProduction()

    @Test
    fun `domain does not import Spring or persistence`() {
        val domainFiles = production.files.filter { it.packagee?.name?.split('.')?.contains("domain") == true }
        assertCondition(domainFiles.isNotEmpty(), "No domain files selected")
        domainFiles.flatMap { it.imports }.assertTrue { import ->
            forbiddenDomainImports.none(import.name::startsWith) &&
                !(import.name.startsWith("br.com.ajadigital.") && ".adapters." in import.name)
        }
    }

    @Test
    fun `dependencies are injected through constructors`() {
        production.properties().assertTrue { property ->
            property.annotations.none { annotation ->
                annotation.isInjectionAnnotation() && (!property.isConstructorDefined || annotation.targetsMember())
            }
        }
        production.functions().assertTrue { function ->
            function.annotations.none { it.isInjectionAnnotation() }
        }
    }

    @Test
    fun `controller signatures expose response data`() {
        val endpoints = production.classes()
            .filter { controller -> controller.annotations.any { it.importedName() in controllerAnnotations } }
            .flatMap { Class.forName(assertNotNull(it.fullyQualifiedName).replace("`", ""), false, javaClass.classLoader).declaredMethods.toList() }
            .filter { AnnotatedElementUtils.hasAnnotation(it, RequestMapping::class.java) }
        assertCondition(endpoints.isNotEmpty(), "No controller endpoints selected")
        val violations = endpoints.mapNotNull(::returnViolation)
        assertCondition(violations.isEmpty(), violations.joinToString("\n"))
    }

    private fun returnViolation(method: Method): String? {
        val type = assertNotNull(method.kotlinFunction).returnType
        return if (type.isTransportReturnType()) null else "${method.declaringClass.simpleName}.${method.name}: $type"
    }

    private fun KType.isTransportReturnType(): Boolean {
        val name = (classifier as? KClass<*>)?.qualifiedName ?: return false
        val internalType = name.startsWith("br.com.ajadigital.") && internalPackages.any { it in name }
        return !internalType && persistencePackages.none(name::startsWith) && arguments.all { it.type?.isTransportReturnType() == true }
    }

    private fun KoAnnotationDeclaration.isInjectionAnnotation(): Boolean = importedName() in injectionAnnotations

    private fun KoAnnotationDeclaration.importedName(): String = containingFile.imports.firstOrNull { it.alias?.name == name }?.name?.substringAfterLast('.') ?: name

    private fun KoAnnotationDeclaration.targetsMember(): Boolean = listOf("@field:", "@get:", "@set:", "@setparam:", "@delegate:", "@all:").any(text::startsWith)

    private companion object {
        val persistencePackages = listOf(
            "java.sql.", "javax.sql.", "jakarta.persistence.", "javax.persistence.", "org.hibernate.",
            "org.jooq.", "org.jetbrains.exposed.", "org.flywaydb.", "org.postgresql.",
        )
        val forbiddenDomainImports = persistencePackages + "org.springframework."
        val internalPackages = listOf(".domain.", ".adapters.out.", ".application.usecases.", ".application.ports.")
        val injectionAnnotations = setOf("Autowired", "Inject", "Resource", "Value", "PersistenceContext", "PersistenceUnit")
        val controllerAnnotations = setOf("Controller", "RestController")
    }
}
