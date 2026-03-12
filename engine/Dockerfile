FROM eclipse-temurin:25-jre
WORKDIR /app
# 맥북에서 빌드된 JAR만 복사
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
