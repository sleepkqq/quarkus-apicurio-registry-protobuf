package com.sleepkqq.apicurio.registry.protobuf.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.net.URLClassLoader;

import org.graalvm.nativeimage.hosted.Feature;
import org.junit.jupiter.api.Test;

import com.google.protobuf.GeneratedMessage;

import ru.meenity.apicurio.registry.protobuf.runtime.graal.ProtobufBuildTimeInitFeature;

class ProtobufBuildTimeInitFeatureTest {

	@Test
	void initializesJavaEditionExtensionBeforeAnalysis() throws Exception {
		URL[] classpath = {
				ProtobufBuildTimeInitFeature.class.getProtectionDomain().getCodeSource().getLocation(),
				GeneratedMessage.class.getProtectionDomain().getCodeSource().getLocation(),
				Feature.class.getProtectionDomain().getCodeSource().getLocation()
		};
		try (var loader = new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader())) {
			Class<?> feature = loader.loadClass(ProtobufBuildTimeInitFeature.class.getName());
			Class<?> setupAccess = loader.loadClass(Feature.DuringSetupAccess.class.getName());
			feature.getMethod("duringSetup", setupAccess)
					.invoke(feature.getConstructor().newInstance(), new Object[] { null });

			Class<?> javaFeatures = Class.forName("com.google.protobuf.JavaFeaturesProto", true, loader);
			Object extension = javaFeatures.getField("java_").get(null);
			Object descriptor = extension.getClass().getMethod("getDescriptor").invoke(extension);
			assertEquals("pb.java", descriptor.getClass().getMethod("getFullName").invoke(descriptor));
		}
	}
}
