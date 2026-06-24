# Quarkus Apicurio Registry — Protobuf

[![CI](https://github.com/sleepkqq/quarkus-apicurio-registry-protobuf/actions/workflows/ci.yml/badge.svg)](https://github.com/sleepkqq/quarkus-apicurio-registry-protobuf/actions/workflows/ci.yml)
[![Release](https://github.com/sleepkqq/quarkus-apicurio-registry-protobuf/actions/workflows/release.yml/badge.svg)](https://github.com/sleepkqq/quarkus-apicurio-registry-protobuf/actions/workflows/release.yml)
[![JitPack](https://jitpack.io/v/sleepkqq/quarkus-apicurio-registry-protobuf.svg)](https://jitpack.io/#sleepkqq/quarkus-apicurio-registry-protobuf)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A [Quarkus](https://quarkus.io) extension that makes the **Confluent Kafka Protobuf serde**
(`io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer` / `KafkaProtobufDeserializer`)
work in **GraalVM native image** — talking to **Apicurio Registry**'s Confluent-compatible
(`ccompat`) API or to a real Confluent Schema Registry. The focus of this extension is making that
whole serde stack survive native compilation, where it otherwise falls apart.

There is no official Quarkus extension for the Confluent Protobuf serde (the platform ships
`quarkus-apicurio-registry-avro` and `-json-schema` only, and those wrap Apicurio's own serde, not
Confluent's). This fills that gap.

## The problem

Confluent's Protobuf serde — and the protobuf runtime underneath it — is heavily reflective: Kafka
loads the (de)serializer by class name, the serde instantiates context / subject / schema-id
strategies reflectively, the schema-registry client and `ProtobufSchema` eagerly resolve
descriptors, and your generated message classes are looked up by name. Under GraalVM native those
classes get stripped, so a naive native build fails at runtime with
`ClassNotFoundException` / missing-reflection errors (cf. quarkusio/quarkus#22093).

## What it does

- **Registers the Confluent Protobuf serde stack** for native reflection —
  `KafkaProtobufSerializer` / `Deserializer` + their `*Config`, the `CachedSchemaRegistryClient`,
  `RestService`, `ProtobufSchema` / `ProtobufSchemaProvider`, and the context / subject /
  schema-id strategies the serde wires up reflectively.
- **Registers the `com.google.protobuf` runtime types** the serde touches (`DynamicMessage`,
  `Descriptors`, `GeneratedMessage`, extension-registry plumbing, …).
- **Auto-scans** the application index for `com.google.protobuf.GeneratedMessage` subclasses and
  registers each message + its `Builder`/enum for reflection — so consumers need **no**
  hand-written reflection config to get typed deserialization.
- **Indexes Confluent's own proto jars** (`kafka-schema-serializer`, `kafka-protobuf-types`) and
  keeps the optional Avro compressor backends and the apicurio serde packages run-time
  initialized, so native augmentation doesn't choke on classes that aren't on the build classpath.
- **Enables SSL in native** for the registry client out of the box.

## Compatibility

<!-- versions:start -->
| Extension | Quarkus  | Confluent serde | protobuf-java | Java |
|-----------|----------|-----------------|---------------|------|
| `1.1.2`   | `3.36.3` | `8.3.0`         | `4.35.1`      | `21` |
<!-- versions:end -->

Registry side: **Apicurio Registry 3.x** via its `ccompat` endpoint
(`/apis/ccompat/v7`), or any Confluent-compatible Schema Registry.

> The table is kept in sync with the `pom.xml` properties (`${revision}` is the single source of
> truth for the version) by `./scripts/sync-readme.sh` — run it after bumping a dependency instead
> of editing this file by hand.

## Coordinates

Published through [JitPack](https://jitpack.io/#sleepkqq/quarkus-apicurio-registry-protobuf). The
version is a git tag or commit hash; JitPack rewrites the groupId to `com.github.<user>.<repo>`:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.sleepkqq.quarkus-apicurio-registry-protobuf:quarkus-apicurio-registry-protobuf:1.1.2")
    implementation("io.quarkus:quarkus-messaging-kafka") // or quarkus-kafka-client
}
```

> Note: JitPack does not preserve the original `com.sleepkqq` groupId, so the extension's
> *deployment* artifact (referenced by the generated extension descriptor) is not auto-resolvable
> from JitPack. It works for the common single-jar consumer case; for a strict multi-module setup
> build from source with `./mvnw install` to get the original `com.sleepkqq:…` coordinates.

## Configuration

### Extension build-time config

Set under `quarkus.apicurio.registry.protobuf.*` (`application.yaml` / `.properties`):

| Property | Default | Description |
|---|---|---|
| `index-dependencies` | — | `groupId:artifactId` list of schema-library jars to Jandex-index so their message classes are discovered. Use for libraries that ship no Jandex index. |
| `register-message-classes` | `true` | Auto-scan the index for `GeneratedMessage` subclasses and register them for reflection. |
| `message-classes` | — | Explicit FQCN list of message classes to register (escape hatch; always works regardless of indexing). |

### Confluent serde config (consumer-facing)

Standard Confluent / SmallRye Kafka keys, set on the channel or globally:

| Key | Purpose |
|---|---|
| `schema.registry.url` | Registry base — Apicurio `ccompat` (`http://registry:8080/apis/ccompat/v7`) or Confluent SR |
| `auto.register.schemas` | Auto-register the schema on serialize |
| `<channel>.value.serializer` / `.value.deserializer` | `io.confluent.kafka.serializers.protobuf.KafkaProtobuf{Serializer,Deserializer}` |
| `<channel>.specific.protobuf.value.type` | FQCN of the generated message to deserialize into (typed instead of `DynamicMessage`) |

## Usage

Ship your protobuf messages in a jar (so the serde resolves them from Quarkus' base classloader),
point the extension at it, and wire a Kafka channel with the Confluent serde.

`application.properties` (consumer):

```properties
# discover message classes from the schema jar (it ships no Jandex index)
quarkus.apicurio.registry.protobuf.index-dependencies=com.example:catalog-protobuf-model

# registry (Apicurio ccompat endpoint, or a Confluent Schema Registry)
mp.messaging.connector.smallrye-kafka.schema.registry.url=http://localhost:8080/apis/ccompat/v7

# incoming channel: Confluent Protobuf deserializer, typed to a generated message
mp.messaging.incoming.books-in.connector=smallrye-kafka
mp.messaging.incoming.books-in.topic=books
mp.messaging.incoming.books-in.value.deserializer=io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
mp.messaging.incoming.books-in.specific.protobuf.value.type=com.example.catalog.Book
mp.messaging.incoming.books-in.auto.offset.reset=earliest
mp.messaging.incoming.books-in.group.id=books-consumer
```

```java
@ApplicationScoped
public class BookConsumer {

    @Incoming("books-in")
    public void consume(Book book) {
        // `book` is a typed com.example.catalog.Book — no DynamicMessage, no manual reflection config
        Log.infof("received book: %s", book.getTitle());
    }
}
```

Producing is symmetric — set `value.serializer` to the Confluent
`KafkaProtobufSerializer` and `auto.register.schemas=true`.

## Native image

```bash
./mvnw -B clean install                                            # JVM build
./mvnw -B -f integration-tests/pom.xml verify -Dnative \
    -Dquarkus.native.container-build=true                          # native round-trip (Docker)
```

The extension supplies the reflection / runtime-init metadata the Confluent serde and protobuf
runtime need, so consumers get a working native binary without sprinkling
`@RegisterForReflection` over their app.

## Build & integration tests

```bash
./mvnw -B clean install -DskipTests                 # runtime + deployment
./mvnw -B -f example-model/pom.xml install          # build the protobuf example model first
./mvnw -B -f integration-tests/pom.xml test         # JVM round-trip (Docker required)
```

The integration test is self-contained — it doesn't depend on any external schema library. The
sibling `example-model/` project (`com.example:catalog-protobuf-model`) generates protobuf
messages two ways via the
[ascopes protobuf-maven-plugin](https://github.com/ascopes/protobuf-maven-plugin):

- **Java codegen** — `com.example.catalog.Book` (plain `--java_out`)
- **Kotlin codegen** — `com.example.weather.Reading` (`kotlinEnabled`, used through the generated
  Kotlin DSL `reading { ... }`)

Each is produced and consumed through Kafka (Dev Services) + Apicurio Registry 3.x
(Testcontainers, `ccompat` endpoint), asserting a typed round-trip in both JVM **and** GraalVM
native. The model jar ships no Jandex index, so the test points the extension at it with
`quarkus.apicurio.registry.protobuf.index-dependencies=com.example:catalog-protobuf-model`.

Build order: `example-model` first, then the integration tests.

> **Bleeding-edge Docker note**: older Testcontainers / `docker-java` negotiated Docker Remote API
> `1.32`, which daemons ≥ Docker 25/29 reject (`minimum supported API version is 1.40`). The IT
> pins `-Dapi.version=1.43` via the surefire/failsafe `argLine` (a harmless no-op on
> Testcontainers 2.x, which negotiates automatically).

## CI / Release

- **CI** (`.github/workflows/ci.yml`) — on PRs and pushes to `master`: builds the extension and the
  example model, runs the JVM integration tests (Docker on the runner).
- **Release** (`.github/workflows/release.yml`) — on a tag push: builds and creates a GitHub
  Release with generated notes. Consume the tagged version via
  [JitPack](https://jitpack.io/#sleepkqq/quarkus-apicurio-registry-protobuf).
- **Dependabot** (`.github/dependabot.yml`) — weekly Maven + GitHub Actions updates, grouped.

## License

[Apache License 2.0](LICENSE).
