package ru.meenity.apicurio.registry.protobuf.it;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import social.FriendRequestApproved;

@Path("/protobuf")
public class ApicurioRegistryProtobufResource {

	private static final String TOPIC = "friend-request-approved";

	@ConfigProperty(name = "kafka.bootstrap.servers")
	String kafkaBootstrap;

	@ConfigProperty(name = "apicurio.registry.url")
	String registryUrl;

	@GET
	@Path("/roundtrip")
	@Produces(MediaType.TEXT_PLAIN)
	public String roundtrip() throws Exception {
		FriendRequestApproved sent = FriendRequestApproved.newBuilder()
				.setFriendRequestId("fr-1")
				.setAuthorName("Alice")
				.setRecipientId("bob")
				.build();

		produce(sent);
		FriendRequestApproved received = consume();
		return received.getClass().getName() + "|" + received.getAuthorName() + "|" + received.getRecipientId();
	}

	private void produce(FriendRequestApproved message) throws Exception {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
				"io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");
		props.put("apicurio.registry.url", registryUrl);
		props.put("apicurio.registry.auto-register", "true");

		try (KafkaProducer<String, FriendRequestApproved> producer = new KafkaProducer<>(props)) {
			producer.send(new ProducerRecord<>(TOPIC, "key-1", message)).get();
			producer.flush();
		}
	}

	private FriendRequestApproved consume() {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
				"io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer");
		props.put("apicurio.registry.url", registryUrl);
		props.put("apicurio.registry.deserializer.value.return-class", FriendRequestApproved.class.getName());

		try (KafkaConsumer<String, FriendRequestApproved> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of(TOPIC));
			long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
			while (System.currentTimeMillis() < deadline) {
				ConsumerRecords<String, FriendRequestApproved> records = consumer.poll(Duration.ofSeconds(2));
				for (ConsumerRecord<String, FriendRequestApproved> record : records) {
					return record.value();
				}
			}
			throw new IllegalStateException("No record consumed within timeout");
		}
	}
}
