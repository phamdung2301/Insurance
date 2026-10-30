package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.PolicyNote;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyNoteRepository extends MongoRepository<PolicyNote, String> {

    /** Mới nhất trước. */
    List<PolicyNote> findByPolicyNumberOrderByCreatedAtDesc(String policyNumber);
}
