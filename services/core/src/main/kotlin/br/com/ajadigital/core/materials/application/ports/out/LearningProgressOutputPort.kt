package br.com.ajadigital.core.materials.application.ports.out

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.contracts.DashboardContent
import br.com.ajadigital.core.materials.application.contracts.LearningProgress
import br.com.ajadigital.core.materials.application.models.StoreProgressQuery
import br.com.ajadigital.core.materials.application.models.StoreRecordProgressCommand
import br.com.ajadigital.core.shared.application.contracts.Page

interface LearningProgressOutputPort {
    fun progress(request: StoreProgressQuery): Page<LearningProgress>
    fun dashboard(actor: AuthenticatedActor, progressLimit: Int): DashboardContent
    fun recordProgress(request: StoreRecordProgressCommand): LearningProgress?
}
