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
# Render free = 512 Mo : privilégier Metaspace (Spring AI + AWS SDK) plutôt qu'un gros heap.
# 128m Metaspace → OOM sous charge (upload image / R2). Heap abaissé pour rester sous 512 Mo.
# Render free = 512 Mo : heap + metaspace + direct + stacks doivent rester < 512.
# MaxDirectMemorySize/Xss limitent la mémoire native hors heap.
ENV JAVA_OPTS="-Xms64m -Xmx176m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:CompressedClassSpaceSize=64m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+ExitOnOutOfMemoryError"
ENV MALLOC_ARENA_MAX=2
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
