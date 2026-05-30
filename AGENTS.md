# Agent Guidelines — quarkus-apicurio-registry-protobuf

A Quarkus extension that makes the **Apicurio Registry Kafka Protobuf serde** work in **GraalVM
native** mode (the official Quarkus Apicurio extensions only cover Avro and JSON Schema).

## Modules

```
runtime/             — extension runtime: pulls the Apicurio protobuf serde, no extension logic
deployment/          — build steps (ApicurioRegistryProtobufProcessor): native reflection,
                       runtime-init, message-class auto-scan, build-time config
integration-tests/   — Java app + @QuarkusTest/@QuarkusIntegrationTest round-trips (JVM + native)
example-model/       — STANDALONE Maven project (NOT a reactor module): example schema jar with
                       Java- and Kotlin-generated protobuf, consumed by integration-tests
```

Coordinates: `com.sleepkqq:quarkus-apicurio-registry-protobuf` (+ `-deployment`). GitHub repo /
Packages: `sleepkqq/quarkus-apicurio-registry-protobuf`. Java packages are `ru.meenity.*`
(intentionally not renamed to match the groupId).

## Build & test

```bash
mvn clean install -DskipTests                 # runtime + deployment (reactor)
mvn -f example-model/pom.xml clean install     # the example schema jar (separate build, run first)
mvn -f integration-tests/pom.xml test          # JVM round-trips (Docker required)
mvn -f integration-tests/pom.xml verify -Dnative -Dquarkus.native.container-build=true   # native
```

- Native build uses the Mandrel builder image via Docker (`container-build`); no local GraalVM needed.
- `integration-tests` is NOT in the parent `<modules>` (released separately) — always build it with `-f`.
- Build `example-model` and install the extension before running the integration tests.

## Conventions

- **Java + Maven only.** Do not rewrite the extension in Kotlin/Gradle: `quarkus create extension`
  is Maven-only, and Kotlin `@BuildStep` classes silently drop from `quarkus-build-steps.list`
  (quarkusio/quarkus#35110). Target Java 21.
- **No explanatory comments in code.** Put the "why" in commits / this file, not inline.
- Every third-party dependency and plugin version lives in a pom `<properties>` block, referenced
  by `${...}`. Module's own version stays literal in `<parent>`/`<project>`.
- Indentation: tabs.

## How the extension works (deployment build steps)

- Registers the Apicurio protobuf serde stack + `com.google.protobuf` runtime for reflection.
- **Runtime-initializes** protobuf descriptor code (`com.google.protobuf`, the Apicurio gencode
  packages, and discovered message packages) so a single consistent descriptor registry exists —
  protobuf-java 4.x cannot be build-time-initialized.
- **Auto-scans** the Jandex index for `com.google.protobuf.GeneratedMessage` subclasses and
  registers each message + `Builder`/enum for reflection (typed deserialization, no manual config).
- Config (`quarkus.apicurio.registry.protobuf.*`): `index-dependencies` (group:artifact list to
  Jandex-index schema jars that ship no index), `message-classes` (explicit FQCNs),
  `register-message-classes` (toggle auto-scan).

## Hard-won gotchas (don't regress)

- **Message classes must come from a dependency jar**, not application or reactor/workspace
  classes. The Apicurio serde resolves the return-class from Quarkus' base classloader, which sees
  dependency jars only. This is why `example-model` is a standalone build, not a reactor module.
- **Apicurio version skew**: the 3.2.4 protobuf serde pins older (3.1.x) transitive serde modules;
  the parent pom force-aligns the whole `io.apicurio` serde stack to `${apicurio.version}`.
- **protobuf gencode > runtime**: Apicurio's `schema-util-protobuf` gencode (4.34.1) must be ≤ the
  runtime protobuf-java; explicit dependencyManagement pins `protobuf-java` to `${protobuf.version}`.
- **Docker ≥ 25/29 vs Testcontainers**: docker-java negotiates API 1.32 (rejected). The IT pins
  `-Dapi.version=1.43` in the surefire/failsafe `argLine`.
- Protobuf codegen uses the **ascopes protobuf-maven-plugin** (`kotlinEnabled` for the Kotlin DSL);
  `protocVersion` must match `${protobuf.version}`.

## Publishing

`distributionManagement` targets GitHub Packages (`sleepkqq`). `mvn deploy` with GitHub
credentials (a server entry matching the `github` repo id in `~/.m2/settings.xml`).
