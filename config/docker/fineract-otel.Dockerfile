ARG IMAGE_NAME=apache/fineract:latest
FROM ${IMAGE_NAME}

USER root

RUN mkdir -p /otel
RUN apk add --no-cache curl

RUN curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar \
    -o /otel/opentelemetry-javaagent.jar

COPY otel/fineract-otel.properties /otel/fineract-otel.properties

# Create entrypoint script to append OpenTelemetry agent to JAVA_TOOL_OPTIONS
# This ensures the agent is loaded even if JAVA_TOOL_OPTIONS is set via environment variables
RUN echo '#!/bin/sh' > /otel/entrypoint.sh && \
    echo '# Append OpenTelemetry agent to JAVA_TOOL_OPTIONS' >> /otel/entrypoint.sh && \
    echo 'OTEL_AGENT="-javaagent:/otel/opentelemetry-javaagent.jar -Dotel.javaagent.configuration-file=/otel/fineract-otel.properties"' >> /otel/entrypoint.sh && \
    echo 'if [ -n "$JAVA_TOOL_OPTIONS" ]; then' >> /otel/entrypoint.sh && \
    echo '  # Check if agent is already in JAVA_TOOL_OPTIONS' >> /otel/entrypoint.sh && \
    echo '  if echo "$JAVA_TOOL_OPTIONS" | grep -q "opentelemetry-javaagent.jar"; then' >> /otel/entrypoint.sh && \
    echo '    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS"' >> /otel/entrypoint.sh && \
    echo '  else' >> /otel/entrypoint.sh && \
    echo '    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $OTEL_AGENT"' >> /otel/entrypoint.sh && \
    echo '  fi' >> /otel/entrypoint.sh && \
    echo 'else' >> /otel/entrypoint.sh && \
    echo '  export JAVA_TOOL_OPTIONS="$OTEL_AGENT"' >> /otel/entrypoint.sh && \
    echo 'fi' >> /otel/entrypoint.sh && \
    echo '# Execute the original command' >> /otel/entrypoint.sh && \
    echo 'exec "$@"' >> /otel/entrypoint.sh && \
    chmod +x /otel/entrypoint.sh

# Set default JAVA_TOOL_OPTIONS (will be overridden by env vars, but entrypoint will append agent)
ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar -Dotel.javaagent.configuration-file=/otel/fineract-otel.properties"

# Use entrypoint to ensure agent is always included, preserving original CMD
ENTRYPOINT ["/otel/entrypoint.sh"]


