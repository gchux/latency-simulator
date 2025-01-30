#!/bin/sh

JAVA_BIN="$(which java)"

set -x

exec env ${JAVA_BIN} \
  -Dgcp.project_id="${PROJECT_ID}" \
  -cp app:app/lib/* \
  dev.chux.gcp.crun.Application
