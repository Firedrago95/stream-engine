FROM eclipse-temurin:25-jre
WORKDIR /app
ENV TZ=Asia/Seoul
COPY api-server/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
