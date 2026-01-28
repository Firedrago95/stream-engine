# Stage 1: Build the application
FROM gradle:9.3-jdk25 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle bootJar --no-daemon

# Stage 2: Create the final image
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
