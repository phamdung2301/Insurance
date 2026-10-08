package com.dungphd.insuranceass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public SimpleMongoClientDatabaseFactory mongoDbFactory() {
        return new SimpleMongoClientDatabaseFactory("mongodb://localhost:27017/insurance");
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoDbFactory());
    }
}
