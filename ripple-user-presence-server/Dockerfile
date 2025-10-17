# Multi-stage build for ripple-user-presence-server
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy entire project for Maven to resolve all modules
COPY . .

# Build the application
RUN mvn clean package -pl ripple-user-presence-server -am -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built jar
COPY --from=builder /build/ripple-user-presence-server/target/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
