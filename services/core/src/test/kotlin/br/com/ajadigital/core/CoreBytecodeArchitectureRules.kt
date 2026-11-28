package br.com.ajadigital.core

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

internal object CoreBytecodeArchitectureRules {
    fun domainIsolation(): ArchRule = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "tools.jackson..", "com.fasterxml.jackson..", "java.io..", "java.sql..", "javax.sql..",
            "com.rabbitmq..", "jakarta..", "java.net.http..", "..adapters..", "..application..", "..configuration..",
        )

    fun applicationIsolation(): ArchRule = noClasses().that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "tools.jackson..", "com.fasterxml.jackson..", "java.net.http..", "java.sql..", "javax.sql..", "com.rabbitmq..",
            "org.hibernate..", "org.flywaydb..", "org.postgresql..", "jakarta..", "javax.validation..", "..adapters..", "..configuration..",
        )

    fun inboundBoundary(): ArchRule = noClasses().that().resideInAPackage("..adapters.in..")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("UseCase")

    fun inboundToOutboundDirection(): ArchRule = noClasses().that().resideInAPackage("..adapters.in..")
        .should().dependOnClassesThat().resideInAPackage("..adapters.out..")

    fun outboundToInboundDirection(): ArchRule = noClasses().that().resideInAPackage("..adapters.out..")
        .should().dependOnClassesThat().resideInAPackage("..adapters.in..")

    fun noControllerConnectionFactory(): ArchRule = noClasses().that().haveSimpleNameEndingWith("Controller")
        .should().dependOnClassesThat().haveSimpleName("ConnectionFactory")

    fun dashboardDoesNotCallConcreteUseCases(): ArchRule = noClasses()
        .that().haveSimpleNameEndingWith("GetDashboardUseCase")
        .and().resideInAnyPackage("..dashboard.domain.usecases..", "..dashboard.application.usecases..")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("UseCase")

    fun policyEffectFindings(classes: List<JavaClass>): List<String> = classes.flatMap { source ->
        if (!source.packageName.contains(".domain.policies")) emptyList()
        else source.directDependenciesFromSelf.mapNotNull { dependency ->
            val target = dependency.targetClass
            val packageName = target.packageName
            val forbidden = packageName.startsWith("java.net.") && target.name != "java.net.URI"
            if (forbidden) "${source.name} policy depends on I/O type ${target.name}" else null
        }
    }

    fun configurationFindings(classes: List<JavaClass>): List<String> = buildList {
        val configurations = classes.filter { it.isAnnotatedWith(Configuration::class.java) }
        if (configurations.isEmpty()) add("No Spring configuration classes selected")
        configurations.filterNot { it.packageName.isConfigurationPackage() }
            .forEach { add("${it.name} must be in a feature configuration package") }
        val beanHosts = classes.filter { type -> type.methods.any { it.isAnnotatedWith(Bean::class.java) } }
        if (beanHosts.isEmpty()) add("No Spring @Bean method hosts selected")
        beanHosts.filterNot { it.packageName.isConfigurationPackage() }
            .forEach { add("${it.name} declares @Bean outside a feature configuration package") }
        classes.forEach { source ->
            if (source.packageName.isConfigurationPackage()) return@forEach
            source.directDependenciesFromSelf.map { it.targetClass }
                .filter { it.packageName.isConfigurationPackage() }
                .forEach { add("${source.name} depends on configuration ${it.name}") }
        }
    }

    private fun String.isConfigurationPackage() = endsWith(".configuration") || contains(".configuration.")
}
