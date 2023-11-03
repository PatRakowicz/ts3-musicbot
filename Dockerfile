FROM ubuntu:latest
LABEL authors="PatRakowicz"

# Use a base image with a JDK installed (choose the appropriate version)
FROM openjdk:11

# Set the working directory inside the container
WORKDIR /app

# Install JavaFX
RUN apt-get update && \
    apt-get install -y openjfx && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copy the gradle configuration files and download dependencies for caching
COPY build.gradle.kts settings.gradle.kts /app/
COPY gradle /app/gradle
COPY gradlew /app/
RUN ./gradlew --no-daemon dependencies

# Copy the rest of your app's source code
COPY src /app/src

# Build your application
RUN ./gradlew --no-daemon build

ENTRYPOINT ["java", "-jar", "/ts3-musicbot/out/artifacts/ts3-musicbot.jar"]