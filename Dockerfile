# Multi-stage build for optimized production image

# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer if pom.xml hasn't changed)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl for health checks
RUN apk add --no-cache curl

# Create application user for security
RUN addgroup -S appuser && adduser -S -G appuser appuser

# Set working directory
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/instructions-capture-service-*.jar app.jar

# Create logs directory
RUN mkdir -p /var/log && chown -R appuser:appuser /var/log /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/v1/trades/health || exit 1

# JVM optimization flags
ENV JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+OptimizeStringConcat"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
