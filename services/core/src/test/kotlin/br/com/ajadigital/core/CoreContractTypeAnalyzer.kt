package br.com.ajadigital.core

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaGenericArrayType
import com.tngtech.archunit.core.domain.JavaParameterizedType
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.core.domain.JavaTypeVariable
import com.tngtech.archunit.core.domain.JavaWildcardType
import com.tngtech.archunit.core.domain.JavaClasses

/** Traverses only declared port signatures and project contract properties; library types are leaves. */
internal object CoreContractTypeAnalyzer {
    fun analyze(classes: JavaClasses): List<String> {
        val imported = classes.toList()
        val roots = selectRoots(imported)
        check(roots.isNotEmpty()) { "No production port/contracts selected" }
        return analyze(imported, roots)
    }

    fun selectRoots(classes: List<JavaClass>): List<JavaClass> = classes.filter(::isContractRoot)

    fun analyze(classes: List<JavaClass>, roots: List<JavaClass>): List<String> {
        val project = classes.associateBy { it.name }
        return buildList {
            roots.forEach { root ->
                if (root.isInterface && ((root.simpleName.endsWith("InputPort") || root.simpleName.endsWith("OutputPort")) || root.packageName.contains(".application.contracts"))) {
                  root.methods.filterNot { it.name.contains('$') }.forEach { method ->
                    if (root.simpleName == "TransactionRunnerOutputPort" && method.name == "run") {
                        if (!isTransactionRunnerException(method)) add("${root.name}.run: only <T> run(block: () -> T): T is permitted")
                    } else {
                        val bindings = method.typeParameters.associate { variableKey(it) to null }
                        method.parameterTypes.forEach { walk(it, bindings, root, project, add = ::add, visited = mutableSetOf()) }
                        walk(method.returnType, bindings, root, project, add = ::add, visited = mutableSetOf())
                    }
                }
                } else if (!root.isInterface) {
                    val bindings = root.typeParameters.associate { variableKey(it) to null }
                    if (bindings.isEmpty()) visitProperties(root, bindings, root, project, add = ::add, visited = mutableSetOf())
                }
            }
        }.distinct()
    }

    private fun isContractRoot(type: JavaClass): Boolean =
        (type.isInterface && (type.simpleName.endsWith("InputPort") || type.simpleName.endsWith("OutputPort"))) ||
            (type.packageName.contains(".application.contracts") && type.typeParameters.isEmpty())

    private fun isTransactionRunnerException(method: com.tngtech.archunit.core.domain.JavaMethod): Boolean {
        if (method.typeParameters.map { it.name } != listOf("T") || method.parameterTypes.size != 1) return false
        val function = method.parameterTypes.single() as? JavaParameterizedType ?: return false
        val argument = function.actualTypeArguments.singleOrNull() as? JavaWildcardType ?: return false
        val callbackResult = argument.upperBounds.singleOrNull() as? JavaTypeVariable<*> ?: return false
        val result = method.returnType as? JavaTypeVariable<*> ?: return false
        return function.toErasure().name == "kotlin.jvm.functions.Function0" && argument.lowerBounds.isEmpty() &&
            callbackResult.name == "T" && result.name == "T" &&
            callbackResult.genericDeclaration == method && result.genericDeclaration == method
    }

    private fun walk(
        type: JavaType,
        bindings: Map<String, JavaType?>,
        root: JavaClass,
        project: Map<String, JavaClass>,
        add: (String) -> Unit,
        visited: MutableSet<String>,
    ) {
        when (type) {
            is JavaTypeVariable<*> -> {
                val value = bindings[variableKey(type)]
                if (value != null && (value !is JavaTypeVariable<*> || variableKey(value) != variableKey(type))) walk(value, bindings, root, project, add, visited)
                else add("${root.name}: unresolved generic type variable ${type.name}")
            }
            is JavaParameterizedType -> {
                val raw = type.toErasure()
                if (raw.name == "java.lang.Object" || raw.simpleName == "JsonNode") add("${root.name}: generic contract exposes ${raw.name}")
                val next = bindings + raw.typeParameters.mapIndexedNotNull { index, parameter ->
                    type.actualTypeArguments.getOrNull(index)?.let { variableKey(parameter) to it }
                }.toMap()
                type.actualTypeArguments.forEach { argument ->
                    if (argument is JavaWildcardType && argument.upperBounds.any { it.toErasure().name == "java.lang.Object" }) {
                        add("${root.name}: unbounded wildcard in ${type.name}")
                    } else walk(argument, next, root, project, add, visited)
                }
                project[raw.name]?.let { visitProperties(it, next, root, project, add, visited) }
            }
            is JavaWildcardType -> {
                (type.upperBounds + type.lowerBounds).forEach { bound -> walk(bound, bindings, root, project, add, visited) }
            }
            is JavaGenericArrayType -> walk(type.componentType, bindings, root, project, add, visited)
            else -> {
                val raw = type.toErasure()
                if (raw.name == "java.lang.Object" || raw.simpleName == "JsonNode") add("${root.name}: contract exposes ${raw.name}")
                val projectType = project[raw.name] ?: return
                if (projectType.typeParameters.isNotEmpty()) add("${root.name}: raw generic project type ${projectType.name}")
                visitProperties(projectType, bindings, root, project, add, visited)
            }
        }
    }

    private fun visitProperties(
        type: JavaClass,
        bindings: Map<String, JavaType?>,
        root: JavaClass,
        project: Map<String, JavaClass>,
        add: (String) -> Unit,
        visited: MutableSet<String>,
    ) {
        val key = "${type.name}:${bindings.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value?.name}" }}"
        if (!visited.add(key)) return
        val rootFeature = root.packageName.split('.').getOrNull(4)
        val nestedFeature = type.packageName.split('.').getOrNull(4)
        if (rootFeature != null && nestedFeature != null && rootFeature != nestedFeature &&
            (type.packageName.contains(".adapters.") || type.packageName.contains(".domain.") || type.packageName.contains(".application."))) {
            add("${root.name}: references internal cross-feature type ${type.name}")
        }
        type.fields.forEach { walk(it.type, bindings, root, project, add, visited) }
    }

    private fun variableKey(variable: JavaTypeVariable<*>): String = "${variable.genericDeclaration.description}#${variable.name}"
}
