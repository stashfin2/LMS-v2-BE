# syntax=docker/dockerfile:1

##
## Build stage: compile the Spring Boot fat JAR
##
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
ENV GRADLE_USER_HOME=/workspace/.gradle
 
# Copy only the files required to download dependencies first to leverage Docker layer caching.
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle

RUN chmod +x gradlew \
    && ./gradlew --no-daemon --version

# Now copy the full source tree and build the bootable JAR.
COPY . .
RUN ./gradlew :fineract-provider:bootJar -x test --no-daemon \
    && mkdir -p /workspace/app \
    && JAR_FILE=$(ls fineract-provider/build/libs/fineract-provider-*.jar | head -n 1) \
    && cp "${JAR_FILE}" /workspace/app/fineract-provider.jar

##
## Runtime stage: run the application with a slim JRE
##
FROM eclipse-temurin:21-jre

ENV FINERACT_HOME=/opt/fineract \
    SERVER_PORT=8443

RUN groupadd --system fineract \
    && useradd --system --gid fineract --home-dir ${FINERACT_HOME} fineract

WORKDIR ${FINERACT_HOME}

# Copy the bootable jar from the build stage.
COPY --from=build /workspace/app/fineract-provider.jar ./fineract-provider.jar

# Provide a location for logs and temporary files.
RUN mkdir -p ${FINERACT_HOME}/logs
VOLUME ["${FINERACT_HOME}/logs"]

EXPOSE 8443

USER fineract

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- https://localhost:${SERVER_PORT}/fineract-provider/actuator/health --no-check-certificate || exit 1

ENTRYPOINT ["java", "-jar", "fineract-provider.jar"]

