package com.dungphd.insuranceass.repository;

import com.dungphd.insuranceass.model.RateImport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RateImportRepository extends MongoRepository<RateImport, String> {

    /** Mới nhất trước. */
    List<RateImport> findAllByOrderByImportedAtDesc();
}
