FROM eclipse-temurin:25-jdk-noble
WORKDIR /app
COPY backend/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]