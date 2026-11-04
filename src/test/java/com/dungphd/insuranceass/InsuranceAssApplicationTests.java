package com.dungphd.insuranceass;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
        "app.jwt.secret=this-is-a-very-secure-256-bit-secret-key-for-unit-testing-purposes-only",
        "spring.security.oauth2.client.registration.google.client-id=dummy-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=dummy-google-client-secret"
})
@ActiveProfiles("test")
class InsuranceAssApplicationTests {

    @Test
    void contextLoads() {
    }

}
