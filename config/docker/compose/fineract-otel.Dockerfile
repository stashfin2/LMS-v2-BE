ARG IMAGE_NAME=apache/fineract:latest
FROM ${IMAGE_NAME}

# Switch to root to install files
USER root

RUN mkdir -p /otel
RUN apk add --no-cache curl

RUN curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar \
    -o /otel/opentelemetry-javaagent.jar

COPY otel/fineract-otel.properties /otel/fineract-otel.properties



ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar \
 -Dotel.javaagent.configuration-file=/otel/fineract-otel.properties"
