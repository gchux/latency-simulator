#!/bin/sh
JAVA_BIN="$(which java)"
exec env ${JAVA_BIN} -cp app:app/lib/* dev.chux.gcp.crun.Application
