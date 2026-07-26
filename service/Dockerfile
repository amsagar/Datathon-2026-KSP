FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn clean install -DskipTests
FROM eclipse-temurin:25-jdk
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        python3 \
        python3-pip \
        python3-venv \
        nodejs \
        npm \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# AppSail injects the listen port via X_ZOHO_CATALYST_LISTEN_PORT (default 9000). Bind Spring Boot
# to it, falling back to 8080 for local `docker run`. NOTE: build this image for linux/amd64
# (Catalyst only accepts amd64 OCI images) — on Apple Silicon use `docker build --platform linux/amd64`.
ENV X_ZOHO_CATALYST_LISTEN_PORT=8080
# Catalyst's custom runtime launches the container with a scrubbed PATH (/usr/bin:/bin), so a bare
# `java` isn't found even though it's in the image. Pin JAVA_HOME and invoke java by absolute path.
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec \"${JAVA_HOME}/bin/java\" -jar app.jar --server.port=${X_ZOHO_CATALYST_LISTEN_PORT}"]
