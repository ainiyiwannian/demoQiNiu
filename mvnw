#!/bin/sh
# Maven Wrapper script
MAVEN_OPTS="${MAVEN_OPTS:--Xmx512m}"
exec java $MAVEN_OPTS -jar "$(dirname "$0")/.mvn/wrapper/maven-wrapper.jar" "$@"
