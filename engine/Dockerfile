FROM eclipse-temurin:25-jre
WORKDIR /app
ENV TZ=Asia/Seoul
COPY engine/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
