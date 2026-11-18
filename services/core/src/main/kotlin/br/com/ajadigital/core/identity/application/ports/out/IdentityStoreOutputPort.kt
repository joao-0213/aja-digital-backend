package br.com.ajadigital.core.identity.application.ports.out
import br.com.ajadigital.core.identity.domain.models.*
import br.com.ajadigital.core.identity.application.models.*
import br.com.ajadigital.core.identity.application.models.ProfileUpdateResult
import br.com.ajadigital.core.identity.application.models.TokenFailure
import br.com.ajadigital.core.identity.domain.models.SessionRecord
import br.com.ajadigital.core.identity.application.models.RegistrationRecords
import br.com.ajadigital.core.identity.application.models.LoginIdentity
import br.com.ajadigital.core.identity.application.models.AuthTokenResult
import br.com.ajadigital.core.identity.application.models.AuthTokenIssue



interface IdentityStoreOutputPort {
    fun emailExists(request: CheckEmailExistsDto): Boolean
    fun recordAudit(request: RecordIdentityAuditDto)
    fun recordLoginFailure(request: RecordLoginFailureDto)
    fun createRegistration(request: CreateRegistrationDto)
    fun findLoginIdentity(request: FindLoginIdentityDto): LoginIdentity?
    fun rotateSession(request: RotateIdentitySessionDto): Boolean
    fun findSession(request: FindIdentitySessionDto): SessionRecord?
    fun revokeSession(request: RevokeIdentitySessionDto)
    fun issueAuthToken(request: IssueAuthTokenDto): Boolean
    fun confirmEmail(request: ConfirmEmailDto): AuthTokenResult
    fun resetPassword(request: ResetIdentityPasswordDto): TokenFailure?
    fun updateProfile(request: UpdateIdentityProfileDto): ProfileUpdateResult
}
