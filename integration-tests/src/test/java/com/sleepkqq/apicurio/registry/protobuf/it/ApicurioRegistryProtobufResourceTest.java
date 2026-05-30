package com.sleepkqq.apicurio.registry.protobuf.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(ApicurioRegistryTestResource.class)
public class ApicurioRegistryProtobufResourceTest {

	@Test
	public void typedProtobufRoundTrip() {
		given()
				.when().get("/protobuf/roundtrip")
				.then()
				.statusCode(200)
				.body(is("social.FriendRequestApproved|Alice|bob"));
	}
}
