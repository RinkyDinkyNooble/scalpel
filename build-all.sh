#!/usr/bin/env sh
# Builds core (with its unit tests) and every port, then copies the jars to upload into build/release/.
# Each port is its own Gradle build: ./gradlew -p ports/<loader>-<minecraft> build builds just one.
set -e
cd "$(dirname "$0")"
version=$(sed -n 's/^mod_version=//p' gradle.properties | tr -d '\r')
./gradlew build --no-daemon
rm -rf build/release
mkdir -p build/release
for port in ports/*/; do
    port=${port%/}
    echo "== ${port}"
    # A port with its own wrapper (Gradle 9 for ForgeGradle 7) builds with it; the others use the root wrapper.
    if [ -f "$port/gradlew" ]; then
        sh "$port/gradlew" -p "$port" build --no-daemon
    else
        ./gradlew -p "$port" build --no-daemon
    fi
    # Only this version's jars: build/libs keeps the jars of earlier versions.
    for jar in "$port"/build/libs/scalpel-*-"$version".jar; do
        case "$jar" in
            *-slim.jar|*-sources.jar|*-dev.jar) ;;
            *) cp "$jar" build/release/ ;;
        esac
    done
done
ls build/release
