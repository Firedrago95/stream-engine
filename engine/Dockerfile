FROM eclipse-temurin:25-jre
WORKDIR /app
ENV TZ=Asia/Seoul
COPY engine/build/libs/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
