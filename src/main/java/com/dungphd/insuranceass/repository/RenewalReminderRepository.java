package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.RenewalReminder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RenewalReminderRepository extends MongoRepository<RenewalReminder, String> {

    /**
     * Da co reminder cho (policy, moc) nay chua — co che chong gui trung.
     * PHẢI dùng @Query tường minh: tên method suy ra (derived query) bị Spring
     * Data tách "DaysBefore" thành property "days" + keyword "Before" nên context
     * fail lúc boot ("No property 'days' found for type 'RenewalReminder'") —
     * unit test mock repo nên không bắt được, chỉ boot thật mới lộ.
     */
    @Query("{ 'policyNumber': ?0, 'daysBefore': ?1 }")
    boolean existsByPolicyNumberAndDaysBefore(String policyNumber, int daysBefore);
}
