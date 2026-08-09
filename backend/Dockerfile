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
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
