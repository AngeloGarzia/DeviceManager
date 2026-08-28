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
# Render free = 512 Mo : heap bas + metaspace >= 128 Mo (96 Mo → OOM Metaspace au boot).
ENV JAVA_OPTS="-Xms128m -Xmx240m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
