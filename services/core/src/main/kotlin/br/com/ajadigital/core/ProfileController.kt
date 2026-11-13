package br.com.ajadigital.core

import br.com.ajadigital.core.application.identity.UpdateOwnProfile
import br.com.ajadigital.core.application.identity.ProfileEditCommand
import br.com.ajadigital.core.domain.identity.User
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.CookieValue


@RestController
class ProfileController(private val updateOwnProfile: UpdateOwnProfile) {
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
