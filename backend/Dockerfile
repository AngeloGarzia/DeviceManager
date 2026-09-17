# Build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 10001 appuser \
    && mkdir -p /app/uploads \
    && chown -R appuser:appuser /app
COPY --from=build /app/target/device-manager-*.jar app.jar
USER appuser
ENV APP_ENV=production
ENV APP_S3_LOCAL_FALLBACK_DIR=/app/uploads
# Render free = 512 Mo. Heap/Metaspace trop bas → OOM au boot (exit 3 avec ExitOnOutOfMemoryError).
# Garder assez de headroom pour Spring Boot + Flyway ; plafonner direct memory / stacks.
ENV JAVA_OPTS="-Xms96m -Xmx192m -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=40m -XX:MaxDirectMemorySize=48m -Xss512k"
ENV MALLOC_ARENA_MAX=2
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
