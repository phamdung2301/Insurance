package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.config.MongoConfig;
import com.dungphd.insuranceass.model.PendingEndorsement;
import com.dungphd.insuranceass.model.PendingEndorsementStatus;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Track 2 — integration test cho transaction thật. CHỈ chạy khi bật tường
 * minh (xem docs/TRANSACTION-RUNBOOK.md):
 * <pre>
 * mvn -o -Dmaven.repo.local=/home/hatch/workspace/tools/m2repo \
 *   -Dit.mongodb.enabled=true -Dtest=TransactionRollbackIntegrationTest test
 * </pre>
 * Mặc định test này bị DISABLE nên suite thường không bao giờ cần Mongo.
 * Khi bật mà Mongo không phải replica set (standalone) thì test FAIL với
 * thông báo rõ ràng — đó là hệ quả đã biết của @Transactional, không phải
 * test flaky: phải dựng single-node replica set trước (runbook §1).
 *
 * <p>Test này cố tình KHÔNG load toàn bộ app context (chỉ MongoConfig):
 * nó chứng minh cơ chế transaction + CAS ở tầng hạ tầng mà mọi service
 * {@code @Transactional} dùng chung (cùng MongoTransactionManager, cùng
 * MongoTemplate). Hành vi từng service đã có unit test mock riêng.
 */
@EnabledIfSystemProperty(named = "it.mongodb.enabled", matches = "true")
@SpringBootTest(
        classes = TransactionRollbackIntegrationTest.TestConfig.class,
        properties = "spring.data.mongodb.database=insuranceass-txn-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionRollbackIntegrationTest {

    @Configuration
    @Import(MongoConfig.class)
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    void requireReplicaSet() {
        Document hello;
        try {
            hello = mongoTemplate.getDb().runCommand(new Document("hello", 1));
        } catch (Exception e) {
            fail("Integration test da bat (it.mongodb.enabled=true) nhung khong noi duoc MongoDB: "
                    + e.getMessage() + ". Xem docs/TRANSACTION-RUNBOOK.md §1.");
            return;
        }
        assertNotNull(hello.get("setName"),
                "MongoDB khong phai replica set (thieu setName): transaction se FAIL tren standalone. "
                        + "Dung single-node replica set truoc (mongod --replSet + rs.initiate()), xem runbook §1.");
    }

    @BeforeEach
    @AfterEach
    void cleanTestCollections() {
        mongoTemplate.dropCollection("txn_test_a");
        mongoTemplate.dropCollection("txn_test_b");
        mongoTemplate.dropCollection("pending_endorsements");
    }

    @Test
    void transactionManagerBean_isMongoTransactionManager() {
        assertInstanceOf(MongoTransactionManager.class, transactionManager,
                "PlatformTransactionManager phai la MongoTransactionManager tu MongoConfig");
    }

    @Test
    void multiDocumentWrite_commitsBothWhenNoFailure() {
        transactionTemplate.executeWithoutResult(status -> {
            mongoTemplate.insert(new Document("k", "a"), "txn_test_a");
            mongoTemplate.insert(new Document("k", "b"), "txn_test_b");
        });

        assertEquals(1, mongoTemplate.count(new Query(), "txn_test_a"));
        assertEquals(1, mongoTemplate.count(new Query(), "txn_test_b"));
    }

    @Test
    void multiDocumentWrite_rollsBackBothWhenSecondStepFails() {
        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            mongoTemplate.insert(new Document("k", "a"), "txn_test_a");
            mongoTemplate.insert(new Document("k", "b"), "txn_test_b");
            throw new RuntimeException("simulated failure after first write");
        }));

        // Rollback thật: không document nào còn lại, dù insert đầu đã chạy.
        assertEquals(0, mongoTemplate.count(new Query(), "txn_test_a"));
        assertEquals(0, mongoTemplate.count(new Query(), "txn_test_b"));
    }

    @Test
    void casClaim_exactlyOneWinnerUnderConcurrency() throws Exception {
        PendingEndorsement seed = PendingEndorsement.builder()
                .id("pend-race-1")
                .policyNumber("POL-RACE-01")
                .status(PendingEndorsementStatus.PENDING)
                .build();
        mongoTemplate.insert(seed);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PendingEndorsement>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                Query query = new Query(Criteria.where("id").is("pend-race-1")
                        .and("status").is(PendingEndorsementStatus.PENDING));
                Update update = new Update().set("status", PendingEndorsementStatus.APPROVING);
                // Đúng primitive mà PendingEndorsementApproval.claimForApproval dùng.
                return mongoTemplate.findAndModify(
                        query, update, FindAndModifyOptions.options().returnNew(true), PendingEndorsement.class);
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        long winners = futures.stream().filter(f -> {
            try {
                return f.get() != null;
            } catch (Exception e) {
                return false;
            }
        }).count();
        assertEquals(1, winners, "Chi dung 1 checker thang CAS claim, cac checker con lai phai nhan null");

        PendingEndorsement after = mongoTemplate.findById("pend-race-1", PendingEndorsement.class);
        assertNotNull(after);
        assertEquals(PendingEndorsementStatus.APPROVING, after.getStatus());
    }
}
