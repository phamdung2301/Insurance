package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Dau vet da gui nhac tai tuc (dot 3B) — chong gui trung.
 *
 * <p>Mot document = mot (policy, moc ngay). Unique index tren cap
 * (policyNumber, daysBefore) theo dung pattern @CompoundIndexes cua
 * model/Policy.java, nen ngay ca khi 2 tien trinh quet chong nhau thi MongoDB
 * cung chan document trung thu hai.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "renewal_reminders")
@CompoundIndexes({
        @CompoundIndex(name = "policy_daysBefore_unique_idx", def = "{'policyNumber': 1, 'daysBefore': 1}", unique = true)
})
public class RenewalReminder {

    @Id
    private String id;

    private String policyNumber;

    /** Moc nhac: 30, 7 hoac 1 (so ngay con lai den ngay het han). */
    private int daysBefore;

    /** Thoi diem notification duoc gui. */
    private Instant sentAt;
}
