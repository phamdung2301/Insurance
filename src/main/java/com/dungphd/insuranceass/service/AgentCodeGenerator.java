package com.dungphd.insuranceass.service;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.stereotype.Component;

/**
 * Sinh mã đại lý dạng AGT-0001, AGT-0002, ... — an toàn trùng lặp.
 *
 * <p>Cơ chế: một document counter duy nhất ({@code _id: "agent_code"}) trong
 * collection {@code counters}, tăng bằng {@code findAndModify} nguyên tử
 * (upsert + returnNew) nên hai request đồng thời không bao giờ nhận cùng một
 * số. Unique index trên {@code Agent.code} là backstop cuối cùng:
 * {@code AgentService} retry với mã mới khi gặp duplicate key (vd mã đã tồn
 * tại thủ công trước khi counter chạy tới số đó).</p>
 *
 * <p>Không có pattern sinh mã sẵn nào trong codebase để tái dùng (grep toàn
 * src không có counter/findAndModify) nên đây là pattern mới, đặt riêng để
 * migration và CRUD cùng dùng một nguồn số.</p>
 */
@Component
@RequiredArgsConstructor
public class AgentCodeGenerator {

    private static final String COUNTER_ID = "agent_code";
    private static final String COUNTERS_COLLECTION = "counters";

    private final MongoTemplate mongoTemplate;

    /** Mã tiếp theo, vd AGT-0001. Không bao giờ trả về mã đã cấp trong cùng DB. */
    public String nextCode() {
        Document updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(COUNTER_ID)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Document.class,
                COUNTERS_COLLECTION);
        if (updated == null) {
            throw new IllegalStateException("Không sinh được mã đại lý (counter không trả về document).");
        }
        Object seqValue = updated.get("seq");
        if (!(seqValue instanceof Number)) {
            throw new IllegalStateException("Counter đại lý không có field seq dạng số.");
        }
        long seq = ((Number) seqValue).longValue();
        if (seq <= 0) {
            throw new IllegalStateException("Counter đại lý cho số không hợp lệ: " + seq);
        }
        // Tới 9999 giữ 4 chữ số; vượt 9999 thì mở rộng tự nhiên (AGT-10000)
        // thay vì tràn/trùng.
        return seq <= 9999 ? String.format("AGT-%04d", seq) : "AGT-" + seq;
    }
}
