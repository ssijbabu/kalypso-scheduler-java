FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/kalypso-scheduler.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
