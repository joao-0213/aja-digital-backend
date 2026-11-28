package br.com.ajadigital.core.identity.adapters.`in`.controllers
import br.com.ajadigital.core.identity.adapters.`in`.controllers.mappers.toResponse
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.ProfileEditRequest
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.responses.UserResponse
import br.com.ajadigital.core.identity.application.models.ProfileEditCommand
import br.com.ajadigital.core.identity.application.ports.`in`.UpdateOwnProfileInputPort
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ProfileController(private val updateOwnProfile: UpdateOwnProfileInputPort) {
    @PatchMapping("/api/v1/me")
    fun update(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestBody command: ProfileEditRequest,
    ): UserResponse {
        require(command.expectedRevision.isIntegralNumber && command.expectedRevision.canConvertToInt())
        val expectedRevision = command.expectedRevision.intValue()
        require(expectedRevision > 0)
        require(command.name.isMissingNode || command.name.isTextual)
        val name = command.name.takeUnless { it.isMissingNode }?.asText()
        return updateOwnProfile.update(br.com.ajadigital.core.identity.application.models.UpdateOwnProfileDto(cookie, ProfileEditCommand(expectedRevision, name))).toResponse()
    }
}
