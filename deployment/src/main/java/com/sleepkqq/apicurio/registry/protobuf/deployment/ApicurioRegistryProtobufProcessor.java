package com.sleepkqq.apicurio.registry.protobuf.deployment;

import java.util.HashSet;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.ProtocolMessageEnum;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.context.strategy.ContextNameStrategy;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.confluent.kafka.serializers.schema.id.SchemaIdDeserializer;
import io.confluent.kafka.serializers.schema.id.SchemaIdSerializer;
import io.confluent.kafka.serializers.subject.strategy.ReferenceSubjectNameStrategy;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.NativeImageFeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;
import io.quarkus.runtime.graal.InetRunTime;
import ru.meenity.apicurio.registry.protobuf.runtime.graal.ProtobufBuildTimeInitFeature;

class ApicurioRegistryProtobufProcessor {

	private static final String FEATURE = "apicurio-registry-protobuf";

	private static final Class<?>[] SERDE_STRATEGY_INTERFACES = {
			ContextNameStrategy.class,
			SubjectNameStrategy.class,
			ReferenceSubjectNameStrategy.class,
			SchemaIdSerializer.class,
			SchemaIdDeserializer.class
	};

	/**
	 * commons-compress (pulled transitively via Avro) ships optional brotli/zstd codec streams whose
	 * backends (zstd-jni in particular) are not on the build classpath. They are kept as plain class
	 * names on purpose: a {@code Class} literal would force the class to load during augmentation and
	 * fail with NoClassDefFoundError. {@link RuntimeInitializedClassBuildItem} only forwards the name
	 * to native-image, so the optional codecs stay run-time initialized without ever being loaded here.
	 */
	private static final String[] OPTIONAL_COMPRESSOR_CLASSES = {
			"org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream",
			"org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream",
			"org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream"
	};

	/**
	 * Packages with no stable type to anchor a {@code Class} literal, so they stay as plain strings:
	 * {@code metadata} is a Confluent generated-proto root package, and the apicurio serde packages are
	 * only present when an apicurio serde is on the consumer's classpath.
	 */
	private static final String[] RUNTIME_INIT_PACKAGES = {
			"metadata",
			"io.apicurio.registry.serde.protobuf.ref",
			"io.apicurio.registry.utils.protobuf.schema"
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
				KafkaProtobufSerializer.class,
				KafkaProtobufDeserializer.class)
				.reason(FEATURE)
				.methods().build());

		reflectiveClass.produce(ReflectiveClassBuildItem.builder(
				ProtobufSchema.class,
				ProtobufSchemaProvider.class,
				CachedSchemaRegistryClient.class,
				RestService.class,
				KafkaProtobufSerializerConfig.class,
				KafkaProtobufDeserializerConfig.class,
				AbstractKafkaSchemaSerDeConfig.class)
				.reason(FEATURE)
				.methods().fields().build());
	}

	@BuildStep
	IndexDependencyBuildItem indexSchemaSerializer() {
		return new IndexDependencyBuildItem("io.confluent", "kafka-schema-serializer");
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
		for (Class<?> iface : SERDE_STRATEGY_INTERFACES) {
			reflectiveClass.produce(ReflectiveClassBuildItem.builder(iface)
					.reason(FEATURE)
					.methods().fields().build());
			for (ClassInfo impl : index.getAllKnownImplementations(iface)) {
				reflectiveClass.produce(ReflectiveClassBuildItem.builder(impl.name().toString())
						.reason(FEATURE)
						.methods().fields().constructors().build());
			}
		}
	}

	@BuildStep
	void registerProtobufRuntime(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
		reflectiveClass.produce(ReflectiveClassBuildItem.builder(
				DynamicMessage.class,
				DynamicMessage.Builder.class,
				GeneratedMessage.class,
				GeneratedMessage.Builder.class,
				AbstractMessage.class,
				AbstractMessage.Builder.class,
				Descriptors.Descriptor.class,
				Descriptors.FieldDescriptor.class,
				Descriptors.FileDescriptor.class,
				Descriptors.EnumDescriptor.class,
				Descriptors.EnumValueDescriptor.class)
				.reason(FEATURE)
				.methods().fields().constructors().build());

		// ExtensionRegistryLite.add(ExtensionLite) reflectively looks up ExtensionRegistry#add(Extension)
		// (getClass().getMethod("add", Extension.class)) when registering generated extensions. Without
		// the method registered, registerAllExtensions (e.g. io.confluent.protobuf.MetaProto, invoked by
		// ProtobufSchema.<clinit>) throws NoSuchMethodException -> "Could not invoke ExtensionRegistry#add".
		reflectiveClass.produce(ReflectiveClassBuildItem.builder(
				ExtensionRegistry.class,
				ExtensionRegistryLite.class)
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
		for (String compressor : OPTIONAL_COMPRESSOR_CLASSES) {
			runtimeInitClass.produce(new RuntimeInitializedClassBuildItem(compressor));
		}
	}

	@BuildStep
	void runtimeInitializedInet(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitClass) {
		// Quarkus' InetRunTime class initializer builds the IPv4/IPv6 wildcard addresses via
		// io.smallrye.common.net.Inet, baking an Inet4Address into the image heap. InetAddress
		// must stay run-time initialized (JDK JNI), so defer InetRunTime to run time as well.
		runtimeInitClass.produce(new RuntimeInitializedClassBuildItem(InetRunTime.class.getName()));
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
		runtimeInitPackage.produce(new RuntimeInitializedPackageBuildItem(ProtobufSchema.class.getPackageName()));
		for (String pkg : RUNTIME_INIT_PACKAGES) {
			runtimeInitPackage.produce(new RuntimeInitializedPackageBuildItem(pkg));
		}
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
			for (ClassInfo message : index.getAllKnownSubclasses(GeneratedMessage.class)) {
				registerMessage(reflectiveClass, message.name().toString());
				addPackage(messagePackages, message.name().toString());
			}
			for (ClassInfo protoEnum : index.getAllKnownImplementations(ProtocolMessageEnum.class)) {
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
