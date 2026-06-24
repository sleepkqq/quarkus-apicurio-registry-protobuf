#!/usr/bin/env bash
# Sync the version numbers in README.md from the pom.xml properties.
# `${revision}` is the single source of truth for the project version; the dependency versions
# (quarkus / confluent / protobuf) live next to it. Run this after bumping any of them instead of
# hand-editing the README:
#
#   ./scripts/sync-readme.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."

prop() { grep -oP "(?<=<$1>)[^<]+" pom.xml | head -1; }

REV=$(prop revision)
QUARKUS=$(prop quarkus.version)
CONFLUENT=$(prop confluent.version)
PROTOBUF=$(prop protobuf.version)
JAVA=$(prop maven.compiler.release)

for v in REV QUARKUS CONFLUENT PROTOBUF JAVA; do
  [ -n "${!v}" ] || { echo "error: could not read '$v' from pom.xml" >&2; exit 1; }
done

export REV QUARKUS CONFLUENT PROTOBUF JAVA

# 1. Regenerate the compatibility table between the markers.
perl -0pi -e 's{<!-- versions:start -->.*?<!-- versions:end -->}{<!-- versions:start -->\n| Extension | Quarkus  | Confluent serde | protobuf-java | Java |\n|-----------|----------|-----------------|---------------|------|\n| `$ENV{REV}`   | `$ENV{QUARKUS}` | `$ENV{CONFLUENT}`         | `$ENV{PROTOBUF}`      | `$ENV{JAVA}` |\n<!-- versions:end -->}s' README.md

# 2. Pin the JitPack coordinate version (anchored to the artifactId -> safe).
perl -pi -e 's{(quarkus-apicurio-registry-protobuf:quarkus-apicurio-registry-protobuf:)[0-9][0-9A-Za-z.\-]*}{$1$ENV{REV}}g' README.md

echo "README.md synced: extension=$REV quarkus=$QUARKUS confluent=$CONFLUENT protobuf=$PROTOBUF java=$JAVA"
