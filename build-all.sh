#!/usr/bin/env sh
# Builds core (with its unit tests) and every port, then copies the jars to upload into build/release/.
# Each port is its own Gradle build: ./gradlew -p ports/<loader>-<minecraft> build builds just one.
set -e
cd "$(dirname "$0")"
./gradlew build --no-daemon
rm -rf build/release
mkdir -p build/release
for port in ports/*/; do
    port=${port%/}
    echo "== ${port}"
    ./gradlew -p "$port" build --no-daemon
    for jar in "$port"/build/libs/scalpel-*.jar; do
        case "$jar" in
            *-slim.jar|*-sources.jar|*-dev.jar) ;;
            *) cp "$jar" build/release/ ;;
        esac
    done
done
ls build/release
