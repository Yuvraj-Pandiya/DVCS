# ─────────────────────────────────────────────────────────────────
# Stage 1: Build — Maven + JDK 21
# ─────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom files first (for layer caching)
COPY pom.xml ./
COPY backend-core/pom.xml backend-core/pom.xml
COPY backend-api/pom.xml backend-api/pom.xml
COPY backend-git/pom.xml backend-git/pom.xml
COPY backend-diff/pom.xml backend-diff/pom.xml
COPY backend-pr/pom.xml backend-pr/pom.xml
COPY backend-issue/pom.xml backend-issue/pom.xml
COPY backend-webhook/pom.xml backend-webhook/pom.xml
COPY backend-pipeline/pom.xml backend-pipeline/pom.xml
COPY backend-notification/pom.xml backend-notification/pom.xml
COPY backend-search/pom.xml backend-search/pom.xml

# Download dependencies (cached layer if pom.xml unchanged)
RUN mvn dependency:go-offline -B

# Copy source code
COPY backend-core/src backend-core/src
COPY backend-api/src backend-api/src
COPY backend-git/src backend-git/src
COPY backend-diff/src backend-diff/src
COPY backend-pr/src backend-pr/src
COPY backend-issue/src backend-issue/src
COPY backend-webhook/src backend-webhook/src
COPY backend-pipeline/src backend-pipeline/src
COPY backend-notification/src backend-notification/src
COPY backend-search/src backend-search/src

# Build the application (skip tests for faster builds)
# NOTE: -DskipTests still compiles tests; use maven.test.skip to avoid testCompile.
RUN mvn package -Dmaven.test.skip=true -B

# ─────────────────────────────────────────────────────────────────
# Stage 2: Runtime — Eclipse Temurin JRE 21 (Alpine)
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Install curl for healthchecks
RUN apk add --no-cache curl

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/backend-api/target/*.jar /app/app.jar

# Expose application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
