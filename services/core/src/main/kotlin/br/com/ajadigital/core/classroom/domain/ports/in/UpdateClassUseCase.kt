package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface UpdateClassUseCase {
    fun updateClass(actor: AcademicActor, id: String, revision: Int, name: String?, code: String?, year: Int?, shift: String?): Map<String, Any?>
}
