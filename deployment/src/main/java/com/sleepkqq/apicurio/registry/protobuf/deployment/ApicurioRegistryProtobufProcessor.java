package com.sleepkqq.apicurio.registry.protobuf.deployment;

import java.util.HashSet;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.NativeImageFeatureBuildItem;
import ru.meenity.apicurio.registry.protobuf.runtime.graal.ProtobufBuildTimeInitFeature;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;

class ApicurioRegistryProtobufProcessor {

	private static final String FEATURE = "apicurio-registry-protobuf";

	private static final DotName GENERATED_MESSAGE = DotName.createSimple("com.google.protobuf.GeneratedMessage");
	private static final DotName PROTOCOL_MESSAGE_ENUM = DotName.createSimple("com.google.protobuf.ProtocolMessageEnum");

	private static final DotName[] SERDE_STRATEGY_INTERFACES = {
			DotName.createSimple("io.confluent.kafka.serializers.context.strategy.ContextNameStrategy"),
			DotName.createSimple("io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy"),
			DotName.createSimple("io.confluent.kafka.serializers.subject.strategy.ReferenceSubjectNameStrategy"),
			DotName.createSimple("io.confluent.kafka.serializers.schema.id.SchemaIdSerializer"),
			DotName.createSimple("io.confluent.kafka.serializers.schema.id.SchemaIdDeserializer")
	};

	@BuildStep
	FeatureBuildItem feature() {
		return new FeatureBuildItem(FEATURE);
	}

	@BuildStep
	ExtensionSslNativeSupportBuildItem enableSslInNative() {
		return new ExtensionSslNativeSupportBuildItem(FEATURE);
	}

	@BuildStep
	void registerSerdeClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
		reflectiveClass.produce(ReflectiveClassBuildItem.builder(
				"io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer",
				"io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer")
				.reason(FEATURE)
				.methods().build());

		reflectiveClass.produce(ReflectiveClassBuildItem.builder(
				"io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema",
				"io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider",
				"io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient",
				"io.confluent.kafka.schemaregistry.client.rest.RestService",
				"io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig",
				"io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig",
				"io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig")
				.reason(FEATURE)
				.methods().fields().build());
	}

	@BuildStep
	IndexDependencyBuildItem indexSchemaSerializer() {
		return new IndexDependencyBuildItem("io.confluent", "kafka-schema-serializer");
	}

	@BuildStep
	IndexDependencyBuildItem indexSchemaRegistryClient() {
		return new IndexDependencyBuildItem("io.confluent", "kafka-schema-registry-client");
	}

	private static final String[] REST_ENTITY_CLASSES = {
			"io.confluent.kafka.schemaregistry.client.rest.entities.Association",
			"io.confluent.kafka.schemaregistry.client.rest.entities.Config",
			"io.confluent.kafka.schemaregistry.client.rest.entities.ContextId",
			"io.confluent.kafka.schemaregistry.client.rest.entities.ErrorMessage",
			"io.confluent.kafka.schemaregistry.client.rest.entities.ExecutionEnvironment",
			"io.confluent.kafka.schemaregistry.client.rest.entities.ExtendedSchema",
			"io.confluent.kafka.schemaregistry.client.rest.entities.LifecyclePolicy",
			"io.confluent.kafka.schemaregistry.client.rest.entities.Metadata",
			"io.confluent.kafka.schemaregistry.client.rest.entities.Mode",
			"io.confluent.kafka.schemaregistry.client.rest.entities.OpType",
			"io.confluent.kafka.schemaregistry.client.rest.entities.Rule",
			"io.confluent.kafka.schemaregistry.client.rest.entities.RuleKind",
			"io.confluent.kafka.schemaregistry.client.rest.entities.RuleMode",
			"io.confluent.kafka.schemaregistry.client.rest.entities.RuleSet",
			"io.confluent.kafka.schemaregistry.client.rest.entities.Schema",
			"io.confluent.kafka.schemaregistry.client.rest.entities.SchemaEntity",
			"io.confluent.kafka.schemaregistry.client.rest.entities.SchemaEntity$EntityType",
			"io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference",
			"io.confluent.kafka.schemaregistry.client.rest.entities.SchemaRegistryDeployment",
			"io.confluent.kafka.schemaregistry.client.rest.entities.SchemaRegistryServerVersion",
			"io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString",
			"io.confluent.kafka.schemaregistry.client.rest.entities.SchemaTags",
			"io.confluent.kafka.schemaregistry.client.rest.entities.ServerClusterId",
			"io.confluent.kafka.schemaregistry.client.rest.entities.SubjectVersion"
	};

	/**
	 * On the lookup path (auto.register.schemas=false, use.latest.version=true) the serializer GETs
	 * /subjects/{subject}/versions/latest and Jackson-deserializes the JSON into the Confluent REST
	 * entity classes (Schema, SchemaReference, Metadata, RuleSet, Rule, ...). Each carries a
	 * {@code @JsonCreator} property-based constructor and no no-arg constructor, so without their
	 * constructors registered Jackson fails in native with "no delegate- or property-based Creator".
	 * Register them by name (index-independent: IndexDependencyBuildItem did not reliably surface this
	 * jar in the combined index, leaving the package-scan loop empty).
	 */
	@BuildStep
	void registerRestEntities(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
		reflectiveClass.produce(ReflectiveClassBuildItem.builder(REST_ENTITY_CLASSES)
				.reason(FEATURE)
				.methods().fields().constructors().build());
	}

	/**
	 * Confluent's ProtobufSchema static initializer eagerly resolves the descriptors of well-known
	 * types (io.confluent.protobuf.MetaProto, io.confluent.protobuf.type.DecimalProto, com.google.type.*),
	 * which live in kafka-protobuf-types. Index that jar so {@link #registerMessageClasses} registers
	 * those GeneratedMessage subclasses for reflection; otherwise ProtobufSchema.&lt;clinit&gt; fails in
	 * native and serialization throws "Could not initialize class ProtobufSchema".
	 */
	@BuildStep
	IndexDependencyBuildItem indexProtobufTypes() {
		return new IndexDependencyBuildItem("io.confluent", "kafka-protobuf-types");
	}

	/**
	 * Confluent serde reflectively instantiates the context/subject/schema-id strategies via a public
	 * no-arg constructor (AbstractConfig.getConfiguredInstance). Rather than listing each default by
	 * name, register every implementor of the strategy interfaces with constructors so any default or
	 * configured strategy resolves in native.
	 */
	@BuildStep
	void registerSerdeStrategies(CombinedIndexBuildItem combinedIndex,
			BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
		IndexView index = combinedIndex.getIndex();
		for (DotName iface : SERDE_STRATEGY_INTERFACES) {
			reflectiveClass.produce(ReflectiveClassBuildItem.builder(iface.toString())
					.reason(FEATURE)
					.methods().fields().build());
			for (ClassInfo impl : index.getAllKnownImplementors(iface)) {
				reflectiveClass.produce(ReflectiveClassBuildItem.builder(impl.name().toString())
						.reason(FEATURE)
						.methods().fields().constructors().build());
			}
		}
	}

	@BuildStep
	void registerProtobufRuntime(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
		reflectiveClass.produce(ReflectiveClassBuildItem.builder(
				"com.google.protobuf.DynamicMessage",
				"com.google.protobuf.DynamicMessage$Builder",
				"com.google.protobuf.GeneratedMessage",
				"com.google.protobuf.GeneratedMessage$Builder",
				"com.google.protobuf.AbstractMessage",
				"com.google.protobuf.AbstractMessage$Builder",
				"com.google.protobuf.Descriptors$Descriptor",
				"com.google.protobuf.Descriptors$FieldDescriptor",
				"com.google.protobuf.Descriptors$FileDescriptor",
				"com.google.protobuf.Descriptors$EnumDescriptor",
				"com.google.protobuf.Descriptors$EnumValueDescriptor")
				.reason(FEATURE)
				.methods().fields().constructors().build());

		// ExtensionRegistryLite.add(ExtensionLite) reflectively looks up ExtensionRegistry#add(Extension)
		// (getClass().getMethod("add", Extension.class)) when registering generated extensions. Without
		// the method registered, registerAllExtensions (e.g. io.confluent.protobuf.MetaProto, invoked by
		// ProtobufSchema.<clinit>) throws NoSuchMethodException -> "Could not invoke ExtensionRegistry#add".
		reflectiveClass.produce(ReflectiveClassBuildItem.builder(
				"com.google.protobuf.ExtensionRegistry",
				"com.google.protobuf.ExtensionRegistryLite")
				.reason(FEATURE)
				.methods().build());
	}

	/**
	 * Avro (pulled by kafka-schema-registry-client) brings commons-compress, whose optional brotli/zstd
	 * codec streams reference native libraries and must be initialized at run time, not baked into the
	 * image heap at build time. Defer them here so consumers need no native-image args of their own.
	 */
	@BuildStep
	void runtimeInitializedCompressors(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitClass) {
		runtimeInitClass.produce(new RuntimeInitializedClassBuildItem(
				"org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream"));
		runtimeInitClass.produce(new RuntimeInitializedClassBuildItem(
				"org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream"));
		runtimeInitClass.produce(new RuntimeInitializedClassBuildItem(
				"org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream"));
	}

	@BuildStep
	void runtimeInitializedInet(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitClass) {
		// Quarkus' InetRunTime class initializer builds the IPv4/IPv6 wildcard addresses via
		// io.smallrye.common.net.Inet, baking an Inet4Address into the image heap. InetAddress
		// must stay run-time initialized (JDK JNI), so defer InetRunTime to run time as well.
		runtimeInitClass.produce(new RuntimeInitializedClassBuildItem("io.quarkus.runtime.graal.InetRunTime"));
	}

	/**
	 * com.google.protobuf is build-time initialized via {@link ProtobufBuildTimeInitFeature}:
	 * generated descriptor holders bake MapEntry/Descriptor instances into the image heap, so the
	 * protobuf runtime must be build-time initialized too. Run-time init here would clash with that.
	 */
	@BuildStep
	NativeImageFeatureBuildItem protobufBuildTimeInitFeature() {
		return new NativeImageFeatureBuildItem(ProtobufBuildTimeInitFeature.class);
	}

	@BuildStep
	void runtimeInitializedProtobuf(BuildProducer<RuntimeInitializedPackageBuildItem> runtimeInitPackage) {
		runtimeInitPackage.produce(new RuntimeInitializedPackageBuildItem("io.confluent.kafka.schemaregistry.protobuf"));
		runtimeInitPackage.produce(new RuntimeInitializedPackageBuildItem("metadata"));
		runtimeInitPackage.produce(new RuntimeInitializedPackageBuildItem("io.apicurio.registry.serde.protobuf.ref"));
		runtimeInitPackage.produce(new RuntimeInitializedPackageBuildItem("io.apicurio.registry.utils.protobuf.schema"));
	}

	@BuildStep
	void indexSchemaDependencies(ApicurioRegistryProtobufBuildTimeConfig config,
			BuildProducer<IndexDependencyBuildItem> indexDependency) {
		config.indexDependencies().ifPresent(deps -> {
			for (String dep : deps) {
				String[] gav = dep.split(":");
				if (gav.length >= 2) {
					indexDependency.produce(new IndexDependencyBuildItem(gav[0].trim(), gav[1].trim()));
				}
			}
		});
	}

	@BuildStep
	void registerMessageClasses(ApicurioRegistryProtobufBuildTimeConfig config,
			CombinedIndexBuildItem combinedIndex,
			BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
			BuildProducer<RuntimeInitializedPackageBuildItem> runtimeInitPackage) {

		Set<String> messagePackages = new HashSet<>();

		if (config.registerMessageClasses().orElse(Boolean.TRUE)) {
			IndexView index = combinedIndex.getIndex();
			for (ClassInfo message : index.getAllKnownSubclasses(GENERATED_MESSAGE)) {
				registerMessage(reflectiveClass, message.name().toString());
				addPackage(messagePackages, message.name().toString());
			}
			for (ClassInfo protoEnum : index.getAllKnownImplementors(PROTOCOL_MESSAGE_ENUM)) {
				reflectiveClass.produce(ReflectiveClassBuildItem.builder(protoEnum.name().toString())
						.reason(FEATURE)
						.methods().fields().build());
				addPackage(messagePackages, protoEnum.name().toString());
			}
		}

		config.messageClasses().ifPresent(classes -> {
			for (String fqcn : classes) {
				registerMessage(reflectiveClass, fqcn.trim());
				addPackage(messagePackages, fqcn.trim());
			}
		});

		for (String pkg : messagePackages) {
			runtimeInitPackage.produce(new RuntimeInitializedPackageBuildItem(pkg));
		}
	}

	private static void addPackage(Set<String> packages, String fqcn) {
		int lastDot = fqcn.lastIndexOf('.');
		if (lastDot > 0) {
			packages.add(fqcn.substring(0, lastDot));
		}
	}

	private static void registerMessage(BuildProducer<ReflectiveClassBuildItem> reflectiveClass, String messageClass) {
		reflectiveClass.produce(ReflectiveClassBuildItem.builder(
				messageClass,
				messageClass + "$Builder")
				.reason(FEATURE)
				.methods().fields().constructors().build());
	}
}
