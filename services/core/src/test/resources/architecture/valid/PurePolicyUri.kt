package fixture.feature.domain.policies

import java.net.URI

internal object PurePolicyUri {
    fun valid(value: String) = runCatching { URI(value) }.isSuccess
}
