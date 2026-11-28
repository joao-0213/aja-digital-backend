package fixture.feature.application.contracts

interface BoundGenericContract {
    fun <T : Any> valueOrNull(value: T?): T?
}
