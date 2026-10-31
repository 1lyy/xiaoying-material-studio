FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY maven-settings-aliyun.xml /tmp/maven-settings-aliyun.xml
ARG MAVEN_MIRROR=central
RUN case "$MAVEN_MIRROR" in \
      central) ;; \
      aliyun) mkdir -p /root/.m2 && cp /tmp/maven-settings-aliyun.xml /root/.m2/settings.xml ;; \
      *) echo "Unsupported MAVEN_MIRROR: $MAVEN_MIRROR" >&2; exit 1 ;; \
    esac
COPY src src
RUN --mount=type=cache,id=xiaoying-maven-repository,target=/root/.m2/repository,sharing=locked \
    mvn -B -ntp -U -Daether.transport.http.retryHandler.count=8 -DskipTests package \
    && mkdir -p /opt/maven-repository \
    && cp -a /root/.m2/repository/. /opt/maven-repository/
ENV MAVEN_OPTS="-Dmaven.repo.local=/opt/maven-repository -Daether.transport.http.retryHandler.count=8"
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
