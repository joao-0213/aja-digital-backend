FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
ARG SERVICE
COPY services/${SERVICE}/ .
COPY docs/http/openapi.json docs/http/openapi.json
RUN --mount=type=cache,target=/root/.gradle,sharing=locked chmod +x gradlew && ./gradlew --no-daemon bootJar && \
    find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
