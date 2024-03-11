#!/bin/sh

JAVA_BIN="$(which java)"
LD_LIBRARY_PATH="${LD_LIBRARY_PATH}:/usr/local/lib"

exec env /sandbox ${JAVA_BIN} -cp app:app/lib/* dev.chux.gcp.crun.Application
