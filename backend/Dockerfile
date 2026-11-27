# Build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Run as a non-root user
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 primepay
WORKDIR /app
COPY --from=build /workspace/target/primepay-api-*.jar app.jar
USER primepay
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
