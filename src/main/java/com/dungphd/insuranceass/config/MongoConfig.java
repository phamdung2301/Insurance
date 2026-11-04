package com.dungphd.insuranceass.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MongoDB config — factory + template + transaction manager, chung MỘT factory.
 *
 * <p>Track 2 (transaction thật): thêm {@link MongoTransactionManager} lấy từ
 * đúng bean {@code mongoDbFactory} bên dưới (inject qua tham số, không tự
 * build factory thứ hai — hai factory khác nhau sẽ cho hai MongoClient khác
 * nhau và transaction sẽ không bao phủ các save qua MongoTemplate).
 * {@code @EnableTransactionManagement} ghi tường minh ở đây thay vì dựa hẳn
 * vào TransactionAutoConfiguration của Spring Boot (Boot 4 cũng tự bật khi
 * có PlatformTransactionManager, nhưng annotation này làm ý đồ rõ ràng và
 * không phụ thuộc thứ tự auto-config).
 *
 * <p>HỆ QUẢ BẮT BUỘC (xem docs/TRANSACTION-RUNBOOK.md): multi-document
 * transaction của MongoDB CHỈ chạy trên replica set. Local dev mặc định
 * (standalone {@code localhost:27017}) sẽ FAIL mọi luồng có
 * {@code @Transactional} — kể cả đường guard chỉ đọc — chứ không tự degrade.
 * Local phải chạy single-node replica set; production (Atlas) vốn đã là
 * replica set nên không cần đổi URI/config nào. Không hardcode URI ở đây:
 * factory vẫn đọc đúng env/property như trước.
 */
@Configuration
@EnableTransactionManagement
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

    /**
     * Transaction manager cho mọi {@code @Transactional} trong codebase.
     * Chỉ tạo object, không mở kết nối: việc tạo bean này không làm
     * {@code contextLoads} (exclude Mongo auto-config) vỡ, vì factory và
     * manager đều lazy-connect; test đó vẫn pass — xem bằng chứng ở báo cáo
     * Track 2. Mọi context gọi method {@code @Transactional} đều phải có
     * bean này (kể cả test context): thiếu nó, Spring ném lỗi lúc tạo proxy,
     * không im lặng bỏ qua.
     */
    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory mongoDbFactory) {
        return new MongoTransactionManager(mongoDbFactory);
    }
}
