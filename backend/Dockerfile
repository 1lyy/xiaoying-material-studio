FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN mvn -B -ntp -DskipTests package
COPY verify-and-export.sh ./

FROM eclipse-temurin:21.0.12.1_1-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home /app app \
    && mkdir -p /app /data/assets \
    && chown -R app:app /app /data
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/material-studio-backend-0.1.0-SNAPSHOT.jar ./app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
