# ==========================================
# Stage 1: Build the Application
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Install bash and dos2unix for reliable script execution across environments
RUN apk add --no-cache bash dos2unix

# Copy Maven wrapper and pom.xml first to take advantage of Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN dos2unix mvnw && chmod +x mvnw

# Pre-fetch project dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B || true

# Copy project source and reference knowledge documents
COPY src ./src
COPY docs ./docs
COPY Biology_Sample.txt Cloud_Computing.txt Empty_Doc.txt ./

# Package the application jar (skipping test execution for faster, deterministic image builds)
RUN ./mvnw clean package -DskipTests

# ==========================================
# Stage 2: Production Lightweight Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Install curl for container health check
RUN apk add --no-cache curl

# Create dedicated non-root application user for container security
RUN addgroup -S docmind && adduser -S docmind -G docmind

# Copy compiled jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Copy required runtime data and reference knowledge files
COPY --from=builder /app/docs ./docs
COPY --from=builder /app/Biology_Sample.txt /app/Cloud_Computing.txt /app/Empty_Doc.txt ./

# Prepare application runtime directories with appropriate ownership
RUN mkdir -p /app/data/website_knowledge /app/uploads && \
    chown -R docmind:docmind /app

# Switch to non-root user
USER docmind

# Default port exposed by the application
EXPOSE 8080 80

# Production environment defaults
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Container healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
