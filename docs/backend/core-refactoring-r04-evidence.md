# Core refactoring R-04 — Separate HTTP DTOs and add Bean Validation

**Starting revision:** `789fe32` (`codex/backend-b01-b16`)

## Summary of changes
1. **Validation dependency:** Added `org.springframework.boot:spring-boot-starter-validation` to `services/core/build.gradle` via Boot dependency management (no explicit version).
2. **HTTP DTO separation:**
   - Added `TokenRequest` (`token`) and `PasswordResetRequest` (`token`, `password`) under `identity/adapters/in/controllers/models/requests/` with `@field:NotBlank`.
   - Updated `AuthController.confirmEmail` and `AuthController.resetPassword` to accept `TokenRequest` and `PasswordResetRequest` instead of domain commands `TokenCommand` and `PasswordResetCommand`; deleted dead `TokenCommand.kt` and removed unused import.
   - Added `@field:NotBlank` to `RegisterRequest` (`name`, `email`, `password`), `EmailRequest` (`email`), and `LoginRequest` (`email` only; `password` kept unannotated so blank-password logins still reach `AuthenticateStudent.login`, clear `aja_session`, record login failure audit, and return `401 INVALID_CREDENTIALS`).
   - Zero `jakarta.*` imports in any `domain/` package.
3. **Auth-before-validation precedence & exception mapping:**
   - Applied `@Valid` on `@RequestBody` for public `AuthController` endpoints (`register`, `login`, `requestEmailConfirmation`, `confirmEmail`, `requestPasswordReset`, `resetPassword`).
   - Preserved `401 SESSION_REQUIRED` before validation on session-protected routes (`ProfileController.update`, `ClassroomController`, etc.) by not adding `@Valid` on `@RequestBody` where `@CookieValue aja_session` is resolved inside the controller.
   - Mapped `MethodArgumentNotValidException` and `HandlerMethodValidationException` in `AuthExceptionHandler` to HTTP `422` with `{ error: { code: "VALIDATION_ERROR", message: "Request is invalid", requestId } }`.

## ECC TDD (RED -> GREEN)
- **RED:** `AuthValidationTests` (7 tests) failed with 4 failures (`register`, `login`, `email-confirmation-requests`, `password-reset-requests`, etc. did not reject blank DTO fields with `422 VALIDATION_ERROR`).
- **GREEN:** Added Bean Validation annotations, `TokenRequest`/`PasswordResetRequest`, `@Valid` on public `AuthController` endpoints, and `MethodArgumentNotValidException`/`HandlerMethodValidationException` handling in `AuthExceptionHandler`. Re-ran `AuthValidationTests` + `IdentityIntegrationTests` -> passed.
- `SessionAuthPrecedenceTests` verifies `POST /api/v1/classes` on `ClassroomController` without a valid session returns `401 SESSION_REQUIRED` before body validation.

## Post-final-edit verification
- `.\gradlew.bat test --tests "*AuthValidationTests" --tests "*SessionAuthPrecedenceTests" --quiet --console=plain`: exit `0`
- `.\gradlew.bat test --rerun-tasks --quiet --console=plain` (`B16_OPENAPI_COVERAGE` unset): exit `0` (`18` suites, `139` tests, `0` failures, `0` errors, `0` skipped)
- `$env:B16_OPENAPI_COVERAGE = 'true'; .\gradlew.bat build --rerun-tasks --quiet --console=plain` (cleared after): exit `0` (`18` suites, `139` tests, `0` failures, `0` errors, `0` skipped; `CoreOpenApiCoverageFilter` verified all `95` non-`/questions` operations in `docs/http/openapi.json`)

## Review record
Independent read-only reviews ran against the complete R-04 diff (`789fe32`) with targeted rechecks after fixes and post-final-edit Gradle validation:
- **ECC reviewer (`docs/agents/code-reviewer.md`):** **APPROVE** (`0` CRITICAL/HIGH/MEDIUM/LOW findings after targeted recheck).
- **Ponytail reviewer (`.agents/skills/ponytail-review/SKILL.md`):** **Ship** (Round 1 findings 1–3 fixed: removed `@field:NotBlank` from `LoginRequest.password` to preserve `401 INVALID_CREDENTIALS`, updated `SessionAuthPrecedenceTests` to test `ClassroomController.createClass`, deleted dead `TokenCommand.kt` and unused import; verified on targeted recheck).
