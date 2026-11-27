# GP-06 evidence — Opt-in runtime API documentation

## Preflight

- Starting revision `bed84bcc2e944c8d75d353057502f60d521fa22c` on `codex/core-golden-path`. The Git index and Core service tree were clean. Existing documentation and repository changes are outside this issue.
- `graphify query 'springdoc D-12 R-08 GP-06 OpenAPI runtime docs CoreOpenApiCoverageConfiguration public private endpoints OriginFilter' --budget 850` located the existing OpenAPI coverage harness, Origin filter and relevant decisions.
- Core uses Spring Boot 4.1.2. springdoc 3.2.1 is the stable version documented to run on Spring Boot 4.1. Maven Central returned HTTP 200 for the exact starter POM. [Compatibility matrix](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot-) [Release](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.2.1) [Artifact POM](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/3.2.1/springdoc-openapi-starter-webmvc-ui-3.2.1.pom)
- The normative OpenAPI 3.1.0 document at `docs/http/openapi.json` is version 1.0.8. Excluding the Questions path family leaves 72 Core paths and 95 operations. Its security scheme names the HttpOnly `aja_session` cookie.
- Java 21, Gradle, and the PostgreSQL and RabbitMQ Testcontainers images are available for both profile variants of the Core process.

## Acceptance

| Criterion | Status |
| --- | --- |
| Exact springdoc version and real Boot 4.1.2 startup | Verified in both live profiles |
| Default profile hides every docs variant, UI and configuration | Verified by live HTTP |
| Active profile serves the public Core group and Swagger UI with the existing cookie flow | Verified by live HTTP and Chromium |
| Operations, security, statuses, schemas and nullability match the public normative subset | Verified by live JSON and YAML checks |
| Private routes, ungrouped or other docs groups, unauthorized exposure and external validator absent | Verified by live tests and Chromium |
| Full Core test and build, packaging and independent ECC review | Verified on the final candidate |

## Local use

Activate the Core `docs` profile with `SPRING_PROFILES_ACTIVE=docs` and include the exact Core UI origin in the existing `API_ALLOWED_ORIGINS` list. For the default direct Core port and host, that origin is `http://127.0.0.1:8081`. Open `/swagger-ui.html` on that Core instance. Use the existing `/auth/login` operation with a test account to let the browser receive and send its HttpOnly `aja_session` cookie. The UI uses the same-origin `/api/v1` server, so the existing Origin and authorization checks still apply.

## Candidate validation

- The initial profile test needed the named MVC handler mapping because Core also registers an Actuator handler mapping. The grouped endpoint then exposed a conversion error for the normative `x-roles` extension when using the general-purpose Boot mapper. The OpenAPI 3.1 mapper preserves that extension.
- The grouped document is projected from the packaged normative resource. Core route registration is compared against the published path and method pairs. The profile keeps the browser UI on the grouped document and uses the Swagger UI disable value for the validator. [Swagger UI network configuration](https://swagger.io/docs/open-source-tools/swagger-ui/usage/configuration/#network)
- Initial Chromium rendering found that Swagger UI requested `/v3/api-docs/core-public/core-public` and showed a definition-load error even though the grouped endpoint itself returned 200. Springdoc already appends the selected group to the generated URL. Validation must check the advertised URL and browser rendering after that configuration is corrected.
- Removing the explicit UI URL lets Springdoc advertise `/v3/api-docs/core-public` directly. The focused test parses the singleton URL, checks its exact value and fetches it. The JSON and YAML contract tests normalize only set-valued schema `required` lists and omitted empty `required` lists.
- Playwright 1.65.1 with Chromium Headless Shell 159.0.8154.6 ran against the repaired `docs` profile Core process. `/swagger-ui.html` redirected to `/swagger-ui/index.html`, and Chromium rendered 95 operations. The effective UI configuration was `/v3/api-docs/core-public`, `validatorUrl=none`, and `withCredentials=true`. A routing check observed zero requests to any other origin.
- `docker build -f infra/docker/service.Dockerfile --build-arg SERVICE=core -t aja-core-gp06-check .` produced the Core image. Its `/app/app.jar` contains the verified springdoc version and repaired profile. The packaged `META-INF/aja/openapi.json` is byte-identical to `docs/http/openapi.json` with SHA-256 `fc2a52eadde52840ef488c62c50f9442307fb60871562054a2f45e3f8d2dae01`.
- `CoreDocsProfileIntegrationTests.packagedBootJarLoadsTheNormativeSpecInTheDocsProfile` started the Core boot JAR as a separate process against the real PostgreSQL and RabbitMQ test services. The grouped endpoint returned 200 with 72 paths, OpenAPI 3.1.0, `/api/v1` and the `aja_session` security scheme.
- Focused `./gradlew test --tests '*OpenApi*' --tests '*DocsProfile*' --tests '*PlatformIntegrationTests' --console=plain` and final `./gradlew test build --rerun-tasks --console=plain` passed. The full gate recorded 238 tests across 47 suites, with zero failures, errors or skips. The final Docker build, `graphify update .`, the scoped whitespace check, and `git diff --exit-code HEAD -- docs/http/openapi.json docs/http/contract.md services/core/src/main/resources/db/migration` passed.

## Independent review

- The ECC reviewer approved without findings after checking the default and active profile boundaries, raw WebJar handling, public operation and schema projection, browser configuration, normative resource packaging and the live tests.
