package ru.meenity.apicurio.registry.protobuf.runtime.graal;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.google.protobuf.GeneratedMessage;

/**
 * Protobuf generates descriptor holder classes whose static initializers create MapEntry/Descriptor
 * instances; with the protobuf runtime initialized at image run time those instances are rejected
 * ("com.google.protobuf.MapEntry was found in the image heap ... marked for initialization at image
 * run time"). Build-time initialization is the standard protobuf native recipe.
 *
 * Done via a Feature because Quarkus ignores --initialize-at-build-time coming from a dependency's
 * META-INF/native-image/.../native-image.properties; RuntimeClassInitialization is applied by
 * GraalVM directly.
 */
public final class ProtobufBuildTimeInitFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeClassInitialization.initializeAtBuildTime(GeneratedMessage.class.getPackageName());
    }
}
