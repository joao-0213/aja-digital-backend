package fixture.feature.application.usecases

class UnsafeAssertion {
    fun run(value: String?): String = value!!
}
