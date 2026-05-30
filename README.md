# Quarkus Apicurio Registry — Protobuf

A Quarkus extension that makes the **Apicurio Registry Kafka Protobuf serde** work in
**GraalVM native** mode, on par with the official `quarkus-apicurio-registry-avro` and
`quarkus-apicurio-registry-json-schema` extensions (which exist only for Avro and JSON Schema —
there is no official Protobuf one).

Without it, `io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer` /
`ProtobufKafkaDeserializer` fail under native with
`ClassNotFoundException` / missing reflection (quarkusio/quarkus#22093), because Kafka loads the
serde and your generated message classes reflectively by name and GraalVM strips them.

## What it does

- Registers the Apicurio Protobuf serde stack (serializer/deserializer, configs, strategies, id
  handlers, headers, schema resolver, registry-client facade) for native reflection.
- Registers the `com.google.protobuf` runtime types the serde touches (`DynamicMessage`,
  `Descriptors`, `GeneratedMessage`, …).
- **Auto-scans** the application index for `com.google.protobuf.GeneratedMessage` subclasses and
  registers each message + its `Builder`/enum for reflection — so consumers need **no**
  hand-written reflection config to get typed deserialization.

## Stack

Quarkus 3.35.x · Apicurio Registry 3.2.x (`apicurio-registry-protobuf-serde-kafka`) ·
protobuf-java 4.34.x · kafka-clients 4.2.x · Java 21.

## Coordinates

```
com.sleepkqq:apicurio-registry-protobuf:1.0.0-SNAPSHOT
```

### Gradle (consumer, e.g. example-service)

```kotlin
// libs.versions.toml
// apicurio-registry-protobuf = { group = "com.sleepkqq", name = "apicurio-registry-protobuf", version = "1.0.0" }
implementation(libs.apicurio.registry.protobuf)
implementation("io.quarkus:quarkus-messaging-kafka") // or quarkus-kafka-client
```

## Configuration

Extension build-time config (`application.yaml`/`.properties`):

| Property | Description |
|---|---|
| `quarkus.apicurio.registry.protobuf.index-dependencies` | `groupId:artifactId` list of schema-library jars to Jandex-index so their message classes are discovered. Use for libraries (e.g. `com.sleepkqq:kafka-schemas`) that ship no Jandex index. |
| `quarkus.apicurio.registry.protobuf.message-classes` | Explicit FQCN list of message classes to register (escape hatch). |
| `quarkus.apicurio.registry.protobuf.register-message-classes` | Disable auto-scan (default `true`). |

Standard Apicurio serde config (consumer-facing, set on the Kafka producer/consumer):

| Key | Purpose |
|---|---|
| `apicurio.registry.url` | Registry v3 API base, e.g. `http://registry:8080/apis/registry/v3` |
| `apicurio.registry.auto-register` | Auto-register the schema on serialize |
| `apicurio.registry.deserializer.value.return-class` | Return a **typed** message (e.g. `social.FriendRequestApproved`) instead of `DynamicMessage` |
| `apicurio.protobuf.derive.class` | Derive the return class from the schema's `java_outer_classname`/`java_multiple_files` |
| `apicurio.protobuf.fallback.on-schema-error` | Parse with the return-class directly if the registry lookup fails |

> Indexing the schema jar (via `index-dependencies` **or** applying a Jandex index to it) is what
> lets the extension auto-register your generated classes. The escape hatch `message-classes`
> always works regardless of indexing.

## Build

```bash
mvn clean install                                   # runtime + deployment
mvn -f integration-tests/pom.xml test               # JVM round-trip (Docker required)
mvn -f integration-tests/pom.xml verify -Dnative \
    -Dquarkus.native.container-build=true           # native round-trip (Docker required)
```

The integration test is fully self-contained — it does not depend on any external schema
library. A standalone sibling project `example-model/` (`com.example:catalog-protobuf-model`,
built separately, not part of the extension reactor) generates protobuf messages two ways via
the [ascopes protobuf-maven-plugin](https://github.com/ascopes/protobuf-maven-plugin):

- **Java codegen** — `com.example.catalog.Book` (plain `--java_out`)
- **Kotlin codegen** — `com.example.weather.Reading` (`kotlinEnabled`, used through the generated
  Kotlin DSL `reading { ... }`)

The test produces and consumes each through Kafka (Dev Services) + Apicurio Registry 3.x
(Testcontainers) and asserts a typed round-trip — covering both codegen flavours in JVM **and**
GraalVM native. The model jar ships no Jandex index, so the test points the extension at it with
`quarkus.apicurio.registry.protobuf.index-dependencies=com.example:catalog-protobuf-model`.

Note: the message classes must come from a dependency jar (not the application's own classes),
because the Apicurio serde resolves the return-class from Quarkus' base classloader — exactly how
a real consumer ships schemas in a separate artifact.

Build order: `mvn -f example-model/pom.xml install` first, then the integration tests.

### Note on bleeding-edge Docker

Testcontainers' bundled `docker-java` negotiates Docker Remote API `1.32`, which daemons ≥ Docker
25/29 reject (`minimum supported API version is 1.40`). The IT pins `-Dapi.version=1.43` via the
surefire/failsafe `argLine`. On older Docker this is a harmless no-op.
