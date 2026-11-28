# Single-image build for hosting (for example Render): the web app is built and bundled into the Spring Boot jar,
# so the screens and the API share one URL. Uses the "dev" profile (in-memory H2, simulated providers) unless
# SPRING_PROFILES_ACTIVE says otherwise. Demo only: data resets on every restart and no real money moves.

# 1) Web app
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2) API with the web app in classpath:/static
FROM eclipse-temurin:17-jdk AS api
WORKDIR /workspace
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
RUN ./mvnw -B -q package -DskipTests

# 3) Runtime, as a non-root user
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 primepay
WORKDIR /app
COPY --from=api /workspace/target/primepay-api-*.jar app.jar
USER primepay
ENV SPRING_PROFILES_ACTIVE=dev
EXPOSE 8080
# Hosts such as Render pass the port in $PORT.
# Sized for a 512 MB instance (measured about 340 MB in use).
ENTRYPOINT ["sh", "-c", "exec java -Xmx256m -XX:MaxMetaspaceSize=160m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
