package br.com.ajadigital.core.curriculum.domain.policies

import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumForbidden
import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision


object CurriculumPolicy {
    private val colors = setOf("violet", "blue", "amber", "green")
    private val icons = setOf("book", "calculator", "atom", "flask", "leaf", "globe", "languages", "landmark", "palette", "graduation")
    private val publicationId = Regex("^(material|notice|assessment):[A-Za-z0-9][A-Za-z0-9._:-]*$")

    fun requireAdmin(active: Boolean, role: String) {
        if (!active || role != "admin") throw CurriculumForbidden()
    }

    fun requireReader(active: Boolean, role: String, emailConfirmed: Boolean) {
        if (!active || role !in setOf("admin", "teacher", "student") || role == "student" && !emailConfirmed) throw CurriculumForbidden()
    }

    fun requireWriterRole(active: Boolean, role: String) {
        if (!active || role !in setOf("admin", "teacher")) throw CurriculumForbidden()
    }

    fun validateSubjectFields(name: String?, teacherIds: List<String>?, color: String?, icon: String?, description: String?) {
        require(name == null || name.isNotBlank() && name.length <= 100)
        require(description == null || description.length <= 10000)
        require(color == null || color in colors)
        require(icon == null || icon in icons)
        require(teacherIds == null || teacherIds.size <= 100 && teacherIds.distinct().size == teacherIds.size)
    }

    fun validateModuleFields(title: String?, description: String?) {
        require(title == null || title.isNotBlank() && title.length <= 160)
        require(description == null || description.length <= 10000)
    }

    fun validateOrder(revision: Int, items: List<ModuleRevision>) {
        require(revision > 0 && items.isNotEmpty() && items.map { it.id }.distinct().size == items.size && items.all { it.revision > 0 })
    }

    fun validateVisit(publicationIds: List<String>) {
        require(publicationIds.distinct().size == publicationIds.size && publicationIds.all { it.length <= 139 && publicationId.matches(it) })
    }

    fun checkedPage(page: Int) = page.also { require(it > 0) }
    fun checkedSize(size: Int) = size.also { require(it in 1..100) }
    fun checkedSort(sort: String) = sort.also { require(it == "id" || it == "-id") }
    fun checkedModuleSort(sort: String) = sort.also { require(it == "order" || it == "-order") }
}
