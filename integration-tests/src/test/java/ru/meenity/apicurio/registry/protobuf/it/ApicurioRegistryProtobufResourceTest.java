package ru.meenity.apicurio.registry.protobuf.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(ApicurioRegistryTestResource.class)
public class ApicurioRegistryProtobufResourceTest {

	@Test
	public void javaGeneratedProtobufRoundTrip() {
		given()
				.when().get("/protobuf/java-roundtrip")
				.then()
				.statusCode(200)
				.body(is("com.example.catalog.Book|Dune|Herbert"));
	}

	@Test
	public void kotlinGeneratedProtobufRoundTrip() {
		given()
				.when().get("/protobuf/kotlin-roundtrip")
				.then()
				.statusCode(200)
				.body(is("com.example.weather.Reading|KSFO|Foggy"));
	}
}
