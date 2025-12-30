# Use JRE (Runtime only) - No JDK or Gradle needed
FROM eclipse-temurin:25-jre

WORKDIR /app

# Copy the JAR built on your host machine
# The context is ./config-server, so the path is relative to that folder
COPY build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]