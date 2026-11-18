package br.com.ajadigital.core.identity

import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveSessionInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.identity.application.usecases.LoginUseCase
import br.com.ajadigital.core.identity.application.usecases.ResolveSessionUseCase
import br.com.ajadigital.core.identity.application.usecases.ResolveAuthenticatedActorUseCase
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import br.com.ajadigital.core.identity.domain.models.*
import br.com.ajadigital.core.identity.application.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class IdentityApplicationUseCaseTests {
    @Test
    fun `login verifies identity and rotates session through application ports`() {
        val user = User("user-1", 1, Instant.EPOCH, Instant.EPOCH, "user-1", "Test", "test@example.test")
        var rotated: RotateIdentitySessionDto? = null
        val store = proxy<IdentityStoreOutputPort> { method, args ->
            when (method) {
                "findLoginIdentity" -> LoginIdentity(user, "password-hash")
                "rotateSession" -> { rotated = args[0] as RotateIdentitySessionDto; true }
                "recordLoginFailure" -> null
                else -> error("Unexpected identity-store call: $method")
            }
        }
        val hasher = proxy<PasswordHasherOutputPort> { method, _ ->
            when (method) {
                "matches" -> true
                else -> error("Unexpected password-hasher call: $method")
            }
        }

        val result = LoginUseCase(store, hasher, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
            .login(LoginStudentDto(LoginCommand(" TEST@example.test ", "password", null)))

        assertNotNull(rotated)
        assertEquals(user, result.session.user)
        assertEquals(result.session.tokenHash, rotated!!.record.tokenHash)
        assertEquals(43, result.cookieValue.length)
    }

    @Test
    fun `session resolution rejects an absent cookie at the application boundary`() {
        val store = proxy<IdentityStoreOutputPort> { method, _ -> error("Unexpected identity-store call: $method") }
        val useCase = ResolveSessionUseCase(store, Clock.systemUTC())

        assertThrows(SessionRequired::class.java) { useCase.resolve(ResolveStudentSessionDto(null)) }
    }

    @Test
    fun `authenticated actor does not reveal its session validation reference in toString`() {
        val actor = AuthenticatedActor("user-1", "admin", true, Instant.EPOCH, "sensitive-session-reference")

        org.junit.jupiter.api.Assertions.assertFalse(actor.toString().contains("sensitive-session-reference"))
    }

    @Test
    fun `feature actor resolution exposes only published actor facts`() {
        val user = User("user-1", 1, Instant.EPOCH, Instant.EPOCH, "user-1", "Test", "test@example.test", "teacher")
        val session = SessionRecord("session-id", "opaque-token-hash", user, Instant.EPOCH, Instant.MAX)
        val sessionResolver = object : ResolveSessionInputPort {
            override fun resolve(request: ResolveStudentSessionDto): SessionRecord = session
        }
        val actorPort: ResolveAuthenticatedActorInputPort = ResolveAuthenticatedActorUseCase(sessionResolver)

        val actors = listOf(actorPort.resolve(ResolveStudentSessionDto("cookie")), actorPort.resolve("cookie"))
        val declaredReturnTypes = ResolveAuthenticatedActorInputPort::class.java.methods
            .filter { it.name == "resolve" }.map { it.returnType }.toSet()

        assertEquals(setOf(AuthenticatedActor::class.java), declaredReturnTypes)
        actors.forEach { actor ->
            assertEquals("user-1", actor.id)
            assertEquals("teacher", actor.role)
            org.junit.jupiter.api.Assertions.assertFalse(actor.toString().contains("opaque-token-hash"))
            org.junit.jupiter.api.Assertions.assertFalse(actor.toString().contains("session-id"))
        }
    }

    private inline fun <reified T> proxy(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            if (method.declaringClass == Any::class.java) return@newProxyInstance method.name
            answer(method.name, args ?: emptyArray())
        } as T
}
