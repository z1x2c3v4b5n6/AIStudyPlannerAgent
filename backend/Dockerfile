FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests \
    && cp "$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -print -quit)" /workspace/app.jar

FROM eclipse-temurin:17-jre-alpine AS runtime
RUN apk add --no-cache netcat-openbsd \
    && addgroup -S app \
    && adduser -S app -G app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar ./app.jar

ENV AI_ENABLED=false
EXPOSE 8080

USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
