package com.dungphd.insuranceass.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    @Value("${spring.data.mongodb.host:localhost}")
    private String mongoHost;

    @Value("${spring.data.mongodb.port:27017}")
    private int mongoPort;

    @Value("${spring.data.mongodb.database:insuranceass}")
    private String mongoDatabase;

    @Bean
    public SimpleMongoClientDatabaseFactory mongoDbFactory() {
        if (mongoUri != null && !mongoUri.isBlank()) {
            return new SimpleMongoClientDatabaseFactory(mongoUri);
        }
        String connectionString = String.format("mongodb://%s:%d/%s", mongoHost, mongoPort, mongoDatabase);
        return new SimpleMongoClientDatabaseFactory(connectionString);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoDbFactory());
    }
}
