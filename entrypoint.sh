#!/bin/sh
set -x

export OTEL_EXPORTER_OTLP_PROTOCOL=grpc
export OTEL_EXPORTER_OTLP_ENDPOINT='http://127.0.0.1:4317'
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=none
export OTEL_SERVICE_NAME='gcp-grpc-java-demo'
export OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true

JAVA_BIN="$(which java)"
exec env ${JAVA_BIN} \
  -javaagent:/app/opentelemetry-javaagent.jar \
  -Dgcp.project_id="${PROJECT_ID}" \
  -Dgcp.location="${GCP_LOCATION}" \
  -Dgoogleapis.location="${GOOGLEAPIS_LOCATION}" \
  -Dotel.java.global-autoconfigure.enabled=true \
  -Dotel.resource.attributes=gcp.project_id="${PROJECT_ID}" \
  -Dotel.exporter.otlp.headers=X-Goog-User-Project="${PROJECT_ID}" \
  -Dotel.exporter.otlp.protocol=grpc \
  -Dotel.traces.exporter=otlp \
  -Dotel.metrics.exporter=none \
  -cp app:app/lib/* dev.chux.gcp.crun.Application
