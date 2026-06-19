package ru.meenity.apicurio.registry.protobuf.runtime.graal;

import java.net.Socket;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "io.confluent.kafka.schemaregistry.client.ssl.HostSslSocketFactory")
final class Target_HostSslSocketFactory {

	@Substitute
	private Socket interceptAndSetHost(Socket socket) {
		return socket;
	}
}
