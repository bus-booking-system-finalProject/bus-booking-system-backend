# Stage 1: Runtime Environment
# Using the generic JRE (Java Runtime Environment) tag for a smaller, final image size.
FROM eclipse-temurin:25-jre

# Set the working directory inside the container
WORKDIR /app

# The ARG is passed in from docker-compose.yml and holds the name of the final JAR.
ARG JAR_FILE

# Copy the final JAR file (which is built by Gradle) into the container
# We assume the build context will contain the build/libs directory
COPY build/libs/${JAR_FILE} app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Define the command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]