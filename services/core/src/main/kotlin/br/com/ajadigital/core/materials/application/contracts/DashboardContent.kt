package br.com.ajadigital.core.materials.application.contracts

import java.util.Collections

class DashboardContent(
    progress: List<LearningProgress>,
    val completedMaterials: Int,
    val totalMaterials: Int,
    val resume: ResumeMaterial?,
) {
    val progress: List<LearningProgress> = Collections.unmodifiableList(progress.toList())
}
