# syntax=docker/dockerfile:1
# ---- build ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && \
    java -Djarmode=tools -jar target/orbitcrm-backend-*.jar extract --layers --launcher --destination target/extracted

# ---- runtime -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S orbit && adduser -S orbit -G orbit && mkdir -p /data/storage && chown -R orbit:orbit /data
WORKDIR /app
# Layered copy: dependencies change rarely, application classes often.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER orbit
ENV SPRING_PROFILES_ACTIVE=prod \
    ORBITCRM_STORAGE_PATH=/data/storage \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
    CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
