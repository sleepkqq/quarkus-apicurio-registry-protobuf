package com.sleepkqq.apicurio.registry.protobuf.it;

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

import com.example.catalog.Book;
import com.example.sample.WeatherFactory;
import com.example.weather.Reading;
import com.google.protobuf.Message;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/protobuf")
public class ApicurioRegistryProtobufResource {

	@ConfigProperty(name = "kafka.bootstrap.servers")
	String kafkaBootstrap;

	@ConfigProperty(name = "schema.registry.url")
	String registryUrl;

	@GET
	@Path("/java-roundtrip")
	@Produces(MediaType.TEXT_PLAIN)
	public String javaRoundtrip() throws Exception {
		Book sent = Book.newBuilder()
				.setIsbn("978-0441013593")
				.setTitle("Dune")
				.setAuthor("Herbert")
				.build();
		Book received = roundtrip("catalog-book", sent, Book.class);
		return received.getClass().getName() + "|" + received.getTitle() + "|" + received.getAuthor();
	}

	@GET
	@Path("/kotlin-roundtrip")
	@Produces(MediaType.TEXT_PLAIN)
	public String kotlinRoundtrip() throws Exception {
		Reading sent = WeatherFactory.INSTANCE.sample();
		Reading received = roundtrip("weather-reading", sent, Reading.class);
		return received.getClass().getName() + "|" + received.getStation() + "|" + received.getCondition();
	}

	private <T extends Message> T roundtrip(String topic, T message, Class<T> type) throws Exception {
		produce(topic, message);
		return consume(topic, type);
	}

	private <T extends Message> void produce(String topic, T message) throws Exception {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
				"io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer");
		props.put("schema.registry.url", registryUrl);

		try (KafkaProducer<String, T> producer = new KafkaProducer<>(props)) {
			producer.send(new ProducerRecord<>(topic, "key-1", message)).get();
			producer.flush();
		}
	}

	private <T> T consume(String topic, Class<T> type) {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
				"io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer");
		props.put("schema.registry.url", registryUrl);
		props.put("specific.protobuf.value.type", type.getName());

		try (KafkaConsumer<String, T> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of(topic));
			long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
			while (System.currentTimeMillis() < deadline) {
				ConsumerRecords<String, T> records = consumer.poll(Duration.ofSeconds(2));
				for (ConsumerRecord<String, T> record : records) {
					return record.value();
				}
			}
			throw new IllegalStateException("No record consumed within timeout");
		}
	}
}
