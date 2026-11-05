package com.dungphd.insuranceass.model;

import com.dungphd.insuranceass.repository.AgentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaaS Phase 1: khóa schema + index bằng reflection — nếu ai vô tình xóa field
 * agentId hay đổi annotation index thì test này đỏ ngay, không cần Mongo thật.
 * (Index thật trên Mongo được tạo bởi auto-index-creation từ chính annotation
 * này; verify bằng db.&lt;coll&gt;.getIndexes() ở môi trường có Mongo.)
 */
class SaasPhase1SchemaTest {

    private static Field field(Class<?> clazz, String name) throws Exception {
        Field f = clazz.getDeclaredField(name);
        assertNotNull(f, clazz.getSimpleName() + " thiếu field " + name);
        return f;
    }

    private static Set<String> compoundDefs(Class<?> clazz) {
        // getAnnotationsByType gộp cả @CompoundIndex đơn lẻ lẫn @CompoundIndexes
        // (annotation đơn không được wrap thành container ở runtime).
        CompoundIndex[] indexes = clazz.getAnnotationsByType(CompoundIndex.class);
        assertTrue(indexes.length > 0, clazz.getSimpleName() + " thiếu @CompoundIndex");
        return Arrays.stream(indexes)
                .map(CompoundIndex::def)
                .collect(Collectors.toSet());
    }

    @Test
    void agent_entityShape() throws Exception {
        assertEquals("agents", Agent.class.getAnnotation(Document.class).collection());
        assertNotNull(Agent.class.getDeclaredField("id"));
        Indexed codeIdx = field(Agent.class, "code").getAnnotation(Indexed.class);
        assertNotNull(codeIdx, "Agent.code phải @Indexed");
        assertTrue(codeIdx.unique(), "Agent.code phải unique");
        assertNotNull(field(Agent.class, "displayName"));
        assertNotNull(field(Agent.class, "contactName"));
        assertNotNull(field(Agent.class, "contactEmail"));
        assertNotNull(field(Agent.class, "phone"));
        assertNotNull(field(Agent.class, "address"));
        assertEquals(AgentStatus.class, field(Agent.class, "status").getType());
        assertNotNull(field(Agent.class, "status").getAnnotation(Indexed.class),
                "Agent.status phải @Indexed (index {status})");
        assertNotNull(field(Agent.class, "createdBy"));
        assertNotNull(field(Agent.class, "createdAt"));
        assertNotNull(field(Agent.class, "updatedAt"));

        assertEquals(Set.of(AgentStatus.ACTIVE, AgentStatus.SUSPENDED, AgentStatus.CLOSED),
                Set.of(AgentStatus.values()));
    }

    @Test
    void agentRepository_queriesExist() throws Exception {
        assertNotNull(AgentRepository.class.getMethod("findByCode", String.class));
        assertNotNull(AgentRepository.class.getMethod("existsByCode", String.class));
        assertNotNull(AgentRepository.class.getMethod("findByStatusOrderByCodeAsc", AgentStatus.class));
        assertNotNull(AgentRepository.class.getMethod("findAllByOrderByCodeAsc"));
    }

    @Test
    void policy_agentIdAndIndexes() throws Exception {
        Indexed idx = field(Policy.class, "agentId").getAnnotation(Indexed.class);
        assertNotNull(idx, "Policy.agentId phải @Indexed (index {agentId})");
        // assignedAgentEmail giữ nguyên — Phase 1 không xóa, không ngừng dùng.
        assertNotNull(field(Policy.class, "assignedAgentEmail"));

        Set<String> defs = compoundDefs(Policy.class);
        assertTrue(defs.contains("{'agentId': 1, 'status': 1, 'createdAt': -1}"),
                "thiếu index {agentId,status,createdAt}, có: " + defs);
        assertTrue(defs.contains("{'agentId': 1, 'status': 1, 'updatedAt': -1}"),
                "thiếu index {agentId,status,updatedAt}, có: " + defs);
        assertTrue(defs.contains("{'agentId': 1, 'insured.email': 1}"),
                "thiếu index {agentId,insured.email}, có: " + defs);
    }

    @Test
    void user_agentIdIndexed() throws Exception {
        assertNotNull(field(User.class, "agentId").getAnnotation(Indexed.class),
                "User.agentId phải @Indexed (index {agentId})");
    }

}
