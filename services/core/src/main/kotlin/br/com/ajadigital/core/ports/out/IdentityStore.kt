package br.com.ajadigital.core.ports.out



interface IdentityStore {
    fun emailExists(normalizedEmail: String): Boolean
    fun recordAudit(eventType: String, outcome: String, subjectUserId: String? = null)
    fun recordLoginFailure(previousTokenHash: String?)
    fun createRegistration(records: RegistrationRecords)
    fun findLoginIdentity(normalizedEmail: String): LoginIdentity?
    fun rotateSession(record: SessionRecord, previousTokenHash: String?, expectedPasswordHash: String): Boolean
    fun findSession(tokenHash: String): SessionRecord?
    fun revokeSession(tokenHash: String?)
    fun issueAuthToken(issue: AuthTokenIssue): Boolean
    fun confirmEmail(tokenHash: String): AuthTokenResult
    fun resetPassword(tokenHash: String, passwordHash: String): TokenFailure?
    fun updateProfile(sessionTokenHash: String, expectedRevision: Int, name: String?): ProfileUpdateResult
}
