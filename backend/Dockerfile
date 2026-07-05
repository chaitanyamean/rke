# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build the application
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Download the OpenTelemetry Java agent (pinned). It auto-instruments Spring MVC,
# JPA/Hibernate and outbound Postgres/JDBC calls at the bytecode level, so no
# application code changes are needed for baseline tracing.
ARG OTEL_AGENT_VERSION=2.29.0
ADD --chmod=644 https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_AGENT_VERSION}/opentelemetry-javaagent-${OTEL_AGENT_VERSION}.jar /app/opentelemetry-javaagent.jar

# Run as a non-root user
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

# Attach the agent to every JVM launched from this image. Using JAVA_TOOL_OPTIONS
# keeps it toggleable: unset it (or override) to run without instrumentation.
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
