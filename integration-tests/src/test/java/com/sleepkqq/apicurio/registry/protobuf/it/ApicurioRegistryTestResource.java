package com.sleepkqq.apicurio.registry.protobuf.it;

import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class ApicurioRegistryTestResource implements QuarkusTestResourceLifecycleManager {

	private static final String IMAGE = System.getProperty("apicurio.image", "apicurio/apicurio-registry:3.2.4");
	private static final int PORT = 8080;

	private GenericContainer<?> registry;

	@Override
	public Map<String, String> start() {
		registry = new GenericContainer<>(DockerImageName.parse(IMAGE))
				.withExposedPorts(PORT)
				.waitingFor(Wait.forHttp("/apis/registry/v3/system/info").forPort(PORT).forStatusCode(200));
		registry.start();
		String url = "http://" + registry.getHost() + ":" + registry.getMappedPort(PORT) + "/apis/registry/v3";
		return Map.of("apicurio.registry.url", url);
	}

	@Override
	public void stop() {
		if (registry != null) {
			registry.stop();
		}
	}
}
