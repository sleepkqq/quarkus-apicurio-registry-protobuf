package com.sleepkqq.apicurio.registry.protobuf.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.catalog.Book;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(ApicurioRegistryTestResource.class)
public class SmallryeKafkaProtobufTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String kafkaBootstrap;

    @ConfigProperty(name = "apicurio.registry.url")
    String registryUrl;

    @BeforeEach
    void clear() {
        BookConsumer.received.clear();
    }

    @Test
    public void smallryeConsumerDeserializesProtobufWithReturnClass() throws Exception {
        Book book = Book.newBuilder()
                .setIsbn("978-0451524935")
                .setTitle("1984")
                .setAuthor("Orwell")
                .build();

        publishViaRawKafka("it-books", book);

        Book received = BookConsumer.received.poll(Duration.ofSeconds(30).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        assertNotNull(received, "SmallRye consumer did not receive the book within 30s");
        assertEquals("1984", received.getTitle());
        assertEquals("Orwell", received.getAuthor());
        assertEquals("978-0451524935", received.getIsbn());
    }

    private void publishViaRawKafka(String topic, Book book) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");
        props.put("apicurio.registry.url", registryUrl);
        props.put("apicurio.registry.auto-register", "true");

        try (KafkaProducer<String, Book> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), book)).get();
            producer.flush();
        }
    }
}
