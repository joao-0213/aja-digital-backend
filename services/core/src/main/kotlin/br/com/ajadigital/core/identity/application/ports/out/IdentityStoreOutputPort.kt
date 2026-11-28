package br.com.ajadigital.core.identity.application.ports.out
import br.com.ajadigital.core.identity.application.models.AuthTokenIssue
import br.com.ajadigital.core.identity.application.models.AuthTokenResult
import br.com.ajadigital.core.identity.application.models.CheckEmailExistsDto
import br.com.ajadigital.core.identity.application.models.ConfirmEmailDto
import br.com.ajadigital.core.identity.application.models.CreateRegistrationDto
import br.com.ajadigital.core.identity.application.models.FindIdentitySessionDto
import br.com.ajadigital.core.identity.application.models.FindLoginIdentityDto
import br.com.ajadigital.core.identity.application.models.IssueAuthTokenDto
import br.com.ajadigital.core.identity.application.models.LoginIdentity
import br.com.ajadigital.core.identity.application.models.ProfileUpdateResult
import br.com.ajadigital.core.identity.application.models.RecordIdentityAuditDto
import br.com.ajadigital.core.identity.application.models.RecordLoginFailureDto
import br.com.ajadigital.core.identity.application.models.RegistrationRecords
import br.com.ajadigital.core.identity.application.models.ResetIdentityPasswordDto
import br.com.ajadigital.core.identity.application.models.RevokeIdentitySessionDto
import br.com.ajadigital.core.identity.application.models.RotateIdentitySessionDto
import br.com.ajadigital.core.identity.application.models.TokenFailure
import br.com.ajadigital.core.identity.application.models.UpdateIdentityProfileDto
import br.com.ajadigital.core.identity.domain.models.SessionRecord

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
