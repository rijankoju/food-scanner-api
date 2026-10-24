# Builds and runs the API server with JDK 25.
#
#   docker build -t food-scanner-api-server .
#   docker run --rm -p 8080:8080 --env-file .env \
#     -e DB_URL=jdbc:postgresql://host.docker.internal:55432/foodscan \
#     -v foodscan-uploads:/app/data/uploads food-scanner-api-server
#
# Secrets come from the environment (--env-file); .env is not copied into the image.

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

# Download dependencies first so they're cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
    && cp target/food-scanner-api-server-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p data/uploads && chown -R app:app data
USER app

# One layer per Spring Boot layer, least to most frequently changed.
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# Local image storage (STORAGE_TYPE=local); mount a volume to keep uploads.
VOLUME /app/data/uploads
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
