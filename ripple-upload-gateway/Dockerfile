# Multi-stage build for ripple-upload-gateway
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy entire project for Maven to resolve all modules
COPY . .

# Build the application
RUN mvn clean package -pl ripple-upload-gateway -am -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built jar
COPY --from=builder /build/ripple-upload-gateway/target/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
