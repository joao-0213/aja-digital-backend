package fixture.feature.domain.models

sealed interface BaseEvent
data class ItemCreated(val id: String) : BaseEvent
