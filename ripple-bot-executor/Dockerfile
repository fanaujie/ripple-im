FROM openjdk:17-slim

WORKDIR /app

# Copy the fat JAR
COPY target/ripple-bot-executor-1.0-SNAPSHOT.jar /app/application.jar

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
