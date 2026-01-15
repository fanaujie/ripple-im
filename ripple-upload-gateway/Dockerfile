# Multi-stage build for ripple-upload-gateway
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy entire project for Maven to resolve all modules
COPY . .

# Download OTel Agent
RUN curl -L -o opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# Build the application
ARG PROFILE=cassandra
RUN mvn clean package -pl ripple-upload-gateway -am -DskipTests -P ${PROFILE}

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built jar
COPY --from=builder /build/ripple-upload-gateway/target/*.jar app.jar

# Copy OTel Agent
COPY --from=builder /build/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar"

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
