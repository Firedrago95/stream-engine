FROM eclipse-temurin:25-jre
WORKDIR /app
ENV TZ=Asia/Seoul
COPY collector/build/libs/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
