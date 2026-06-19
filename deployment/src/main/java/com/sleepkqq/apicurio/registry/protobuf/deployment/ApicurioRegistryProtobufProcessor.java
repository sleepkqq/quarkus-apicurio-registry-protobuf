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
				"io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig",
				"io.confluent.kafka.serializers.subject.TopicNameStrategy",
				"io.confluent.kafka.serializers.subject.RecordNameStrategy",
				"io.confluent.kafka.serializers.subject.TopicRecordNameStrategy")
				.reason(FEATURE)
				.methods().fields().build());
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
