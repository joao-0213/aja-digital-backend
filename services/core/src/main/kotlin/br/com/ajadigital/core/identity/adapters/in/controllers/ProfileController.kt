package br.com.ajadigital.core.identity.adapters.`in`.controllers
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.ProfileEditRequest

import br.com.ajadigital.core.identity.domain.ports.`in`.UpdateOwnProfileUseCase
import br.com.ajadigital.core.identity.domain.models.ProfileEditCommand
import br.com.ajadigital.core.identity.domain.models.User
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.CookieValue


@RestController
class ProfileController(private val updateOwnProfile: UpdateOwnProfileUseCase) {
    @PatchMapping("/api/v1/me")
    fun update(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestBody command: ProfileEditRequest,
    ): User {
        require(command.expectedRevision.isIntegralNumber && command.expectedRevision.canConvertToInt())
        val expectedRevision = command.expectedRevision.intValue()
        require(expectedRevision > 0)
        require(command.name.isMissingNode || command.name.isTextual)
        val name = command.name.takeUnless { it.isMissingNode }?.asText()
        return updateOwnProfile.update(cookie, ProfileEditCommand(expectedRevision, name))
    }
}
