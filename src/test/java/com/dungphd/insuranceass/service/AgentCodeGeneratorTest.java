package com.dungphd.insuranceass.service;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SaaS Phase 1: mã đại lý AGT-0001... — format + việc dùng đúng collection
 * counter. Tính nguyên tử của findAndModify là của MongoDB (upsert +
 * returnNew); test này khóa format và tham số gọi, không giả lập race thật.
 */
@ExtendWith(MockitoExtension.class)
class AgentCodeGeneratorTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AgentCodeGenerator generator;

    private void stubSeq(Number seq) {
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Document.class), eq("counters")))
                .thenReturn(new Document("_id", "agent_code").append("seq", seq));
    }

    @Test
    void nextCode_padsToFourDigits() {
        stubSeq(1);
        assertEquals("AGT-0001", generator.nextCode());
        stubSeq(42);
        assertEquals("AGT-0042", generator.nextCode());
        stubSeq(9999);
        assertEquals("AGT-9999", generator.nextCode());
    }

    @Test
    void nextCode_beyond9999_expandsNaturally() {
        stubSeq(10000);
        assertEquals("AGT-10000", generator.nextCode());
    }
}
