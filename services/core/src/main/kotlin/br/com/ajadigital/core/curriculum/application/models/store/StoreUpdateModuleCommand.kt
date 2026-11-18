package br.com.ajadigital.core.curriculum.application.models.store

data class StoreUpdateModuleCommand(val id: String, val actorId: String, val role: String, val sessionHash: String, val expectedRevision: Int, val title: String?, val description: String?) {
    override fun toString() = "StoreUpdateModuleCommand(sessionHash=[redacted])"
}
