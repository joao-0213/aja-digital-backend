package br.com.ajadigital.core.identity.adapters.out.persistence

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
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.identity.domain.models.SessionRecord
import br.com.ajadigital.core.identity.domain.models.User
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Repository
class JdbcIdentityStore(private val jdbc: JdbcTemplate, private val clock: Clock) : IdentityStoreOutputPort {
    override fun emailExists(request: CheckEmailExistsDto): Boolean = jdbc.queryForObject("select exists(select 1 from core.users where normalized_email = ?)", Boolean::class.java, request.normalizedEmail) ?: false

    @Transactional
    override fun recordAudit(request: RecordIdentityAuditDto) {
        val (eventType, outcome, subjectUserId) = request
        insertAudit(eventType, outcome, subjectUserId)
    }

    @Transactional
    override fun recordLoginFailure(request: RecordLoginFailureDto) {
        val previousTokenHash = request.previousTokenHash
        lockAuthInvariant()
        previousTokenHash?.let { jdbc.update("update core.sessions set revoked_at=now() where token_hash=? and revoked_at is null", it) }
        insertAudit("auth.login", "failure")
    }

    @Transactional
    override fun createRegistration(request: CreateRegistrationDto) {
        val records = request.records
        val user = records.user
        jdbc.update(
            """insert into core.users(id, revision, created_at, updated_at, updated_by, name, email, normalized_email,
               password_hash, role, active, email_confirmed_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            user.id, user.revision, Timestamp.from(user.createdAt), Timestamp.from(user.updatedAt), user.updatedBy, user.name, user.email,
            user.email.lowercase(), records.passwordHash, user.role, user.active, user.emailConfirmedAt,
        )
        jdbc.update(
            "insert into core.auth_tokens(id, user_id, purpose, token_hash, email_at_issue, created_at, expires_at) values (?, ?, ?, ?, ?, ?, ?)",
            records.tokenId,
            user.id,
            "email_confirmation",
            records.tokenHash,
            user.email,
            Timestamp.from(user.createdAt),
            Timestamp.from(records.tokenExpiresAt),
        )
        jdbc.update(
            """insert into core.outbox_events(id, event_type, event_version, created_at, key_id, nonce, encrypted_payload)
               values (?, ?, ?, ?, ?, ?, ?)""",
            records.eventId,
            "identity.email-confirmation-requested",
            1,
            Timestamp.from(user.createdAt),
            records.keyId,
            records.nonce,
            records.encryptedPayload,
        )
        insertAudit("auth.registration", "success", user.id)
    }

    override fun findLoginIdentity(request: FindLoginIdentityDto): LoginIdentity? = try {
        jdbc.queryForObject(
            "select id, revision, created_at, updated_at, updated_by, name, email, role, active, email_confirmed_at, password_hash from core.users where normalized_email = ?",
            { rs, _ ->
                val user = User(
                    rs.getString("id"), rs.getInt("revision"), rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(), rs.getString("updated_by"), rs.getString("name"),
                    rs.getString("email"), rs.getString("role"), rs.getBoolean("active"), rs.getTimestamp("email_confirmed_at")?.toInstant(),
                )
                LoginIdentity(user, rs.getString("password_hash"))
            },
            request.normalizedEmail,
        )
    } catch (_: EmptyResultDataAccessException) {
        null
    }

    @Transactional
    override fun rotateSession(request: RotateIdentitySessionDto): Boolean {
        val (record, previousTokenHash, expectedPasswordHash) = request
        lockAuthInvariant()
        val account = jdbc.query(
            "select password_hash,active from core.users where id=? for update",
            { rs, _ -> rs.getString("password_hash") to rs.getBoolean("active") },
            record.user.id,
        ).firstOrNull() ?: return false
        if (account.first != expectedPasswordHash || !account.second) return false
        previousTokenHash?.let { jdbc.update("update core.sessions set revoked_at = ? where token_hash = ? and revoked_at is null", Timestamp.from(record.createdAt), it) }
        jdbc.update(
            "insert into core.sessions(id,user_id,token_hash,created_at,expires_at) values(?,?,?,?,?)",
            record.id,
            record.user.id,
            record.tokenHash,
            Timestamp.from(record.createdAt),
            Timestamp.from(record.expiresAt),
        )
        insertAudit("auth.login", "success", record.user.id)
        return true
    }

    override fun findSession(request: FindIdentitySessionDto): SessionRecord? = try {
        jdbc.queryForObject(
            """select s.id,s.token_hash,s.created_at session_created_at,s.expires_at,s.revoked_at,u.id uid,u.revision,u.created_at,u.updated_at,u.updated_by,
               u.name,u.email,u.role,u.active,u.email_confirmed_at from core.sessions s join core.users u on u.id=s.user_id where s.token_hash=?""",
            { rs, _ ->
                val user = User(
                    rs.getString("uid"), rs.getInt("revision"), rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(), rs.getString("updated_by"), rs.getString("name"), rs.getString("email"),
                    rs.getString("role"), rs.getBoolean("active"), rs.getTimestamp("email_confirmed_at")?.toInstant(),
                )
                SessionRecord(
                    rs.getString("id"),
                    rs.getString("token_hash"),
                    user,
                    rs.getTimestamp("session_created_at").toInstant(),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("revoked_at")?.toInstant(),
                )
            },
            request.tokenHash,
        )
    } catch (_: EmptyResultDataAccessException) {
        null
    }

    @Transactional
    override fun revokeSession(request: RevokeIdentitySessionDto) {
        val tokenHash = request.tokenHash
        lockAuthInvariant()
        val userId = tokenHash?.let {
            jdbc.query("select user_id from core.sessions where token_hash=?", { rs, _ -> rs.getString(1) }, it).firstOrNull()
        }
        tokenHash?.let { jdbc.update("update core.sessions set revoked_at = now() where token_hash = ? and revoked_at is null", it) }
        insertAudit("auth.logout", "success", userId)
    }

    @Transactional
    override fun issueAuthToken(request: IssueAuthTokenDto): Boolean {
        val issue = request.issue
        val account = jdbc.query(
            "select email, active, email_confirmed_at from core.users where id = ? for update",
            { rs, _ -> Triple(rs.getString("email"), rs.getBoolean("active"), rs.getTimestamp("email_confirmed_at")) },
            issue.userId,
        ).firstOrNull() ?: return false
        val alreadyConfirmed = issue.purpose == "email_confirmation" && account.third != null
        if (account.first != issue.emailAtIssue || !account.second || alreadyConfirmed) {
            insertAudit(requestEvent(issue.purpose), "accepted")
            return false
        }

        jdbc.update(
            "update core.auth_tokens set used_at = ? where user_id = ? and purpose = ? and used_at is null",
            Timestamp.from(issue.createdAt),
            issue.userId,
            issue.purpose,
        )
        jdbc.update(
            "insert into core.auth_tokens(id,user_id,purpose,token_hash,email_at_issue,created_at,expires_at) values(?,?,?,?,?,?,?)",
            issue.id,
            issue.userId,
            issue.purpose,
            issue.tokenHash,
            issue.emailAtIssue,
            Timestamp.from(issue.createdAt),
            Timestamp.from(issue.expiresAt),
        )
        jdbc.update(
            "insert into core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload) values(?,?,1,?,?,?,?)",
            issue.eventId,
            issue.eventType,
            Timestamp.from(issue.createdAt),
            issue.keyId,
            issue.nonce,
            issue.encryptedPayload,
        )
        insertAudit(requestEvent(issue.purpose), "accepted")
        return true
    }

    @Transactional
    override fun confirmEmail(request: ConfirmEmailDto): AuthTokenResult {
        val tokenHash = request.tokenHash
        lockAuthInvariant()
        val metadata = jdbc.query(
            "select id token_id,user_id,email_at_issue,expires_at,used_at from core.auth_tokens where token_hash=? and purpose='email_confirmation'",
            { rs, _ -> ConfirmationRow(rs.getString("token_id"), rs.getString("user_id"), rs.getString("email_at_issue"), rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("used_at")?.toInstant()) },
            tokenHash,
        ).firstOrNull() ?: return AuthTokenResult(failure = TokenFailure.INVALID).also { insertAudit("auth.email_confirmation", "invalid") }
        val normalizedEmail = metadata.emailAtIssue.lowercase()
        val user = lockUser(metadata.userId) ?: return AuthTokenResult(failure = TokenFailure.INVALID).also { insertAudit("auth.email_confirmation", "invalid") }
        jdbc.query("SELECT id FROM core.classes WHERE active AND id IN (SELECT class_id FROM core.enrollments WHERE normalized_email=? AND status='pending') ORDER BY id FOR UPDATE", { rs, _ -> rs.getString(1) }, normalizedEmail)
        jdbc.query("SELECT id FROM core.enrollments WHERE normalized_email=? AND status='pending' AND class_id IN (SELECT id FROM core.classes WHERE active) ORDER BY class_id,id FOR UPDATE", { rs, _ -> rs.getString(1) }, normalizedEmail)
        val row = jdbc.query(
            "select id token_id,user_id,email_at_issue,expires_at,used_at from core.auth_tokens where token_hash=? and purpose='email_confirmation' for update",
            { rs, _ -> ConfirmationRow(rs.getString("token_id"), rs.getString("user_id"), rs.getString("email_at_issue"), rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("used_at")?.toInstant()) },
            tokenHash,
        ).firstOrNull() ?: return AuthTokenResult(failure = TokenFailure.INVALID).also { insertAudit("auth.email_confirmation", "invalid", user.id) }
        val verifiedAt = currentTime()
        if (row.expiresAt <= verifiedAt) return AuthTokenResult(failure = TokenFailure.EXPIRED).also { insertAudit("auth.email_confirmation", "expired", user.id) }
        if (row.usedAt != null || row.emailAtIssue != user.email || !user.active) return AuthTokenResult(failure = TokenFailure.INVALID).also { insertAudit("auth.email_confirmation", "invalid", user.id) }

        jdbc.update("update core.auth_tokens set used_at=? where id=? and used_at is null", Timestamp.from(verifiedAt), row.tokenId)
        if (user.emailConfirmedAt == null) {
            jdbc.update("update core.users set email_confirmed_at=?,revision=revision+1,updated_at=?,updated_by=id where id=?", Timestamp.from(verifiedAt), Timestamp.from(verifiedAt), user.id)
            jdbc.update(
                """update core.enrollments e set student_id=?,status='active',enrolled_at=?,revision=e.revision+1,updated_at=?,updated_by=?
                   from core.classes c where e.class_id=c.id and e.normalized_email=? and e.status='pending' and c.active""",
                user.id,
                Timestamp.from(verifiedAt),
                Timestamp.from(verifiedAt),
                user.id,
                normalizedEmail,
            )
        }
        insertAudit("auth.email_confirmation", "success", user.id)
        return AuthTokenResult(user = findUser(user.id))
    }

    @Transactional
    override fun resetPassword(request: ResetIdentityPasswordDto): TokenFailure? {
        val (tokenHash, passwordHash) = request
        lockAuthInvariant()
        val userId = jdbc.query("select user_id from core.auth_tokens where token_hash=? and purpose='password_reset'", { rs, _ -> rs.getString(1) }, tokenHash).firstOrNull()
            ?: return TokenFailure.INVALID.also { insertAudit("auth.password_reset", "invalid") }
        val user = lockUser(userId) ?: return TokenFailure.INVALID.also { insertAudit("auth.password_reset", "invalid") }
        val rows = jdbc.query(
            "select id token_id,email_at_issue,expires_at,used_at from core.auth_tokens where token_hash=? and purpose='password_reset' for update",
            { rs, _ -> ResetRow(rs.getString("token_id"), rs.getString("email_at_issue"), rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("used_at")?.toInstant()) },
            tokenHash,
        )
        val row = rows.firstOrNull() ?: return TokenFailure.INVALID.also { insertAudit("auth.password_reset", "invalid", user.id) }
        val verifiedAt = currentTime()
        if (row.expiresAt <= verifiedAt) return TokenFailure.EXPIRED.also { insertAudit("auth.password_reset", "expired", user.id) }
        if (row.usedAt != null || row.emailAtIssue != user.email || !user.active) return TokenFailure.INVALID.also { insertAudit("auth.password_reset", "invalid", user.id) }

        jdbc.update("update core.auth_tokens set used_at=? where id=? and used_at is null", Timestamp.from(verifiedAt), row.tokenId)
        jdbc.update("update core.auth_tokens set used_at=? where user_id=? and purpose='password_reset' and used_at is null", Timestamp.from(verifiedAt), user.id)
        jdbc.update("update core.users set password_hash=?,revision=revision+1,updated_at=?,updated_by=id where id=?", passwordHash, Timestamp.from(verifiedAt), user.id)
        jdbc.update("update core.sessions set revoked_at=? where user_id=? and revoked_at is null", Timestamp.from(verifiedAt), user.id)
        insertAudit("auth.password_reset", "success", user.id)
        return null
    }

    @Transactional
    override fun updateProfile(request: UpdateIdentityProfileDto): ProfileUpdateResult {
        val (sessionTokenHash, expectedRevision, name) = request
        lockAuthInvariant()
        val user = jdbc.query(
            """select u.id uid,u.revision,u.created_at,u.updated_at,u.updated_by,u.name,u.email,u.role,u.active,u.email_confirmed_at
               from core.sessions s join core.users u on u.id=s.user_id
               where s.token_hash=? and s.revoked_at is null and u.active for update of u""",
            { rs, _ -> mapUser(rs) },
            sessionTokenHash,
        ).firstOrNull()?.takeIf {
            jdbc.query(
                "select id from core.sessions where token_hash=? and revoked_at is null for update",
                { rs, _ -> rs.getString(1) },
                sessionTokenHash,
            ).firstOrNull() != null
        } ?: return ProfileUpdateResult.SessionInvalid.also { insertAudit("auth.profile_update", "failure") }
        val verifiedAt = currentTime()
        if (jdbc.queryForObject(
                "select exists(select 1 from core.sessions where token_hash=? and revoked_at is null and expires_at>?)",
                Boolean::class.java,
                sessionTokenHash,
                Timestamp.from(verifiedAt),
            ) != true
        ) {
            return ProfileUpdateResult.SessionInvalid.also { insertAudit("auth.profile_update", "failure") }
        }
        if (user.revision != expectedRevision) return ProfileUpdateResult.RevisionConflict(user.revision).also { insertAudit("auth.profile_update", "conflict", user.id) }
        if (name == null) return ProfileUpdateResult.Updated(user).also { insertAudit("auth.profile_update", "success", user.id) }

        val updated = jdbc.query(
            """update core.users set name=?,revision=revision+1,updated_at=?,updated_by=id
               where id=? and revision=? and active returning id uid,revision,created_at,updated_at,updated_by,name,email,role,active,email_confirmed_at""",
            { rs, _ -> mapUser(rs) },
            name,
            Timestamp.from(verifiedAt),
            user.id,
            expectedRevision,
        ).firstOrNull() ?: return ProfileUpdateResult.RevisionConflict(
            jdbc.queryForObject("select revision from core.users where id=?", Int::class.java, user.id) ?: expectedRevision,
        ).also { insertAudit("auth.profile_update", "conflict", user.id) }
        insertAudit("auth.profile_update", "success", user.id)
        return ProfileUpdateResult.Updated(updated)
    }

    private fun insertAudit(eventType: String, outcome: String, subjectUserId: String? = null) {
        jdbc.update("insert into core.audit_events(id,event_type,outcome,subject_user_id) values(?,?,?,?)", UUID.randomUUID().toString(), eventType, outcome, subjectUserId)
    }

    private fun currentTime(): java.time.Instant = java.time.Instant.now(clock)

    private fun lockAuthInvariant() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java)
    }

    private fun requestEvent(purpose: String) = if (purpose == "email_confirmation") "auth.email_confirmation_request" else "auth.password_reset_request"

    private fun findUser(id: String): User = jdbc.queryForObject(
        "select id uid,revision,created_at,updated_at,updated_by,name,email,role,active,email_confirmed_at from core.users where id=?",
        { rs, _ -> mapUser(rs) },
        id,
    )!!

    private fun lockUser(id: String): User? = jdbc.query(
        "select id uid,revision,created_at,updated_at,updated_by,name,email,role,active,email_confirmed_at from core.users where id=? for update",
        { rs, _ -> mapUser(rs) },
        id,
    ).firstOrNull()

    private fun mapUser(rs: java.sql.ResultSet) = User(
        rs.getString("uid"), rs.getInt("revision"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
        rs.getString("updated_by"), rs.getString("name"), rs.getString("email"), rs.getString("role"), rs.getBoolean("active"),
        rs.getTimestamp("email_confirmed_at")?.toInstant(),
    )

    private data class ConfirmationRow(val tokenId: String, val userId: String, val emailAtIssue: String, val expiresAt: java.time.Instant, val usedAt: java.time.Instant?)
    private data class ResetRow(val tokenId: String, val emailAtIssue: String, val expiresAt: java.time.Instant, val usedAt: java.time.Instant?)
}
