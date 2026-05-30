package ru.meenity.apicurio.registry.protobuf.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.apicurio.registry.protobuf")
public interface ApicurioRegistryProtobufBuildTimeConfig {

	/**
	 * Dependency artifacts ({@code groupId:artifactId}) that contain generated Protobuf
	 * message classes. They are Jandex-indexed at build time so their
	 * {@code com.google.protobuf.GeneratedMessage} subclasses can be discovered and
	 * registered for reflection automatically. Use this for schema libraries that do not
	 * ship their own Jandex index.
	 */
	@WithName("index-dependencies")
	Optional<List<String>> indexDependencies();

	/**
	 * Fully-qualified Protobuf message class names to register for reflection explicitly.
	 * Escape hatch for classes that auto-scanning cannot reach.
	 */
	@WithName("message-classes")
	Optional<List<String>> messageClasses();

	/**
	 * Whether to auto-scan the application index for {@code GeneratedMessage} subclasses
	 * and register them (plus their {@code Builder}/{@code OrBuilder}) for reflection.
	 */
	@WithName("register-message-classes")
	Optional<Boolean> registerMessageClasses();
}
